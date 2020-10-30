package org.terabit.core

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import kotlinx.coroutines.*
import org.terabit.network.RemoteCmd
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.events.ActiveEvent
import org.terabit.primary.events.FindNodeEvent
import org.terabit.primary.events.InactiveEvent
import org.terabit.primary.events.PingEvent
import org.terabit.primary.getKey
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.greenrobot.eventbus.Subscribe
import org.terabit.common.*
import org.terabit.core.base.TerabitData
import org.terabit.primary.PrimaryConfig
import org.terabit.primary.impl.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.experimental.inv
import kotlin.random.Random

private const val LEVEL_NODE_COUNT = 8
private const val EXPAND_LEVEL_COUNT = 5

private val mLog = Log("KBucket")

class KBucket {

    private var expandJob: Job? = null

    private val levels = Array(256) {HashSet<ThatPrimaryNode>(LEVEL_NODE_COUNT)}

    private val waitList = LinkedList<ThatPrimaryNode>()
    private val waitLock = ReentrantLock()
    private val waitCond = waitLock.newCondition()

    private val expandDFMap = LinkedHashMap<String, CompletableDeferred<Any?>>()

    //minor list. minor node is same as ThatPrimaryNode
    private val minorList = LinkedList<ThatPrimaryNode>()

    private val primaryChannels = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    private val minorChannels = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    val broadcastMessages = ConcurrentHashMap<String, Long>()

    private fun printMe() {
        val sb = StringBuilder()
        sb.append("====================KBucket=========================\n")
        for ((index,lv) in levels.withIndex()) {
            if (lv.isEmpty()) {
                continue
            }
            sb.append(String.format("lv%03d: ", index))
            for (n in lv) {
                sb.append(" [${ByteUtil.firstToHexString(n.kid)}]")
            }
            sb.append('\n')
        }
        sb.append("====================================================")
        println(sb.toString())
    }

    @Synchronized
    private fun add(it: ThatPrimaryNode) {
        mLog.log("--------add: $it")
        if (myAccount.getAddrBytes().contentEquals(it.kid)) { //self
            mLog.log("----add: is me")
            return
        }
        if (it.kid === EMPTY_BYTE_ARRAY) { //no kid
            return
        }
        val level = levelOf(it.kid)
        mLog.log("------------>>>>>>>>>>>>>>>>>>>>>>>>>add level=$level")
        if (levels[level].size >= LEVEL_NODE_COUNT) { //has enough node, need to deal ping
            return
        }
        if (findNodeInLevel(level, it.ip, it.port) != null) {
            return
        }
        if (levels[level].add(it)) {
            printMe()
            primaryChannels.add(it.ctx?.channel())
            doExpand()
        }
    }

    @Synchronized
    private fun delete(it: ThatPrimaryNode) {
        //is this faster than traversal?
        val n = (if (it.kid.isEmpty()) findNode(it.ctx) else it) ?: return
        val lv = levelOf(n.kid)
        if (lv >= 0 && lv < levels.size) {
            val node = findNodeInLevel(lv, n.ip, n.port) ?: return
            levels[lv].remove(node)
            primaryChannels.remove(n.ctx?.channel())
            //notice need to update KBucket
        }
        if (Env.noMinor) {
            return
        }
        //delete from minor list
        val node = findNodeInMinor(n)
        if (node != null) {
            minorList.remove(node)
            minorChannels.remove(node.ctx?.channel())
        }
    }

    private fun levelOf(kid: ByteArray): Int {
        //-1 because of base 0
        return 256 - ByteUtil.numberOfLeadingZeros(ByteUtil.xor(myAccount.getAddrBytes(), kid)) - 1
    }

    fun autoInit() {
        //try to find some random node to setup kBuckets
        //should create for different level
        mLog.log("--------myId: ${ByteUtil.toHexString(myAccount.getAddrBytes())}")

        loadFromLocal()
        if (waitList.size == 0) { //add original nodes
            //test add
            waitList.add(ThatPrimaryNode(PrimaryConfig.seedIp, PRIMARY_PORT))
            //real add
        }

        //connect node thread
        CoroutineScope(Dispatchers.IO).launch {
            connThread()
        }

        //manager broadcast message
        Timer().schedule(object : TimerTask() {
            override fun run() {
                try {
                    checkBroadOvertime()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }, 600000, 6000000)
    }

    private fun isNodeInKBucket(node: ThatPrimaryNode): Boolean {
        for (lv in levels) {
            for (n in lv) {
                if (n.isSameKey(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun addNode2Wait(node: ThatPrimaryNode) {
        mLog.log("--------addNode2Wait: $node")
        if (isNodeInKBucket(node)) {
            return
        }
        waitLock.lock()
        waitList.add(node)
        waitCond.signal()
        waitLock.unlock()
    }

    private fun connThread() {
        var node: ThatPrimaryNode?
        while (true) {
            node = null

            waitLock.lock()
            if (waitList.isNotEmpty()) {
                node = waitList.removeFirst()
            }
            if (node == null) {
                waitCond.await()
            }
            waitLock.unlock()

            if (node == null) {
                continue
            }
            println("--------connect to $node")
            if (connectToThatNode(node)) {
                continue
            }

            if (waitList.isEmpty()) {
                sleep(2000)
                if (nodeCount() == 0) {
                    waitList.add(ThatPrimaryNode(PrimaryConfig.seedIp, PRIMARY_PORT))
                }
            }
        }
    }

    private fun doExpand() {
        val job = expandJob
        if (job != null && !job.isCompleted) {
            return
        }
        expandJob = CoroutineScope(Dispatchers.IO).launch {
            expand()
        }
    }

    private suspend fun expand() {
        val lvList = LinkedList<Int>()
        val visitedList = LinkedList<ThatPrimaryNode>()

        mLog.log("----expand")
        val jobList = LinkedList<CompletableDeferred<Any?>>()
        for (i in 0 until EXPAND_LEVEL_COUNT) {
            val lv = findNotFullLevel(lvList)
            if (lv < 0) {
                continue
            }
            lvList.add(lv)
            //no unvisited nodes, return
            val node = findNearestNotVisitedNode(lv, visitedList) ?: break
            visitedList.add(node)
            val df = CompletableDeferred<Any?>()
            expandDFMap[node.getKey()] = df
            jobList.add(df)
            mLog.log("----send find node cmd to $node")
            node.sendCmd(createFindNodeCmd(createLvId(lv)))
            GlobalScope.launch {
                waitOrTimeoutDeferred(expandDFMap, df, node)
            }
        }
        if (jobList.isNotEmpty()) {
            awaitAll(*jobList.toTypedArray())
        }
        mLog.log("----expand once over")
    }

    private fun createLvId(level: Int): ByteArray {
        val myKid = myAccount.getAddrBytes()
        val bytes = ByteArray(myKid.size)
        System.arraycopy(myKid, 0, bytes, 0, myKid.size)
        val byteCount = (256 - level) / 8
        for (i in byteCount until bytes.size) {
            bytes[i] = bytes[i].inv()
        }
        mLog.log("--------createLvId($byteCount): ${ByteUtil.toHexString(bytes)}")
        return bytes
    }

    private fun findNotFullLevel(lvList: List<Int>?): Int {
        val rand = Random(System.nanoTime()).nextInt(levels.size)
        mLog.log("--------findNotFullLevel from rand=$rand")
        for (i in rand downTo 0) {
            if (levelIsEmpty(i, lvList)) {
                return i
            }
        }
        for (i in rand + 1 until levels.size) {
            if (levelIsEmpty(i, lvList)) {
                return i
            }
        }
        return -1
    }

    private fun levelIsEmpty(lv: Int, lvList: List<Int>?): Boolean {
        if (lvList != null && lvList.contains(lv)) {
            return false
        }
        val max = if (lv < 3) 1.shl(lv) else LEVEL_NODE_COUNT
        return levels[lv].size < max
    }

    private fun findNearestNotVisitedNode(level: Int, visitedList: List<ThatPrimaryNode>?): ThatPrimaryNode? {
        //println("----------levels node count=${nodeCount()}   visited node count=${visitedList?.size}")
        var node = findNotVisitedNodeInLevel(level, visitedList)
        if (node != null) {
            mLog.log("--------findNearestNotVisitedNode: $node")
            return node
        }
        for (i in 1 until levels.size) {
            node = findNotVisitedNodeInLevel(level - i, visitedList)
            if (node != null) {
                break
            }
            node = findNotVisitedNodeInLevel(level + i, visitedList)
            if (node != null) {
                break
            }
        }
        mLog.log("--------findNearestNotVisitedNode: $node")
        return node
    }

    private fun findNotVisitedNodeInLevel(level: Int, visitedList: List<ThatPrimaryNode>?): ThatPrimaryNode? {
        if (level < 0 || level >= levels.size) {
            return null
        }
        val lvNodes = levels[level]
        //mLog.log("--levels[$level].size=${lvNodes.size}")
        for (node in lvNodes) {
            //mLog.log("--node=$node")
            if (visitedList?.contains(node) == true) {
                continue
            }
            mLog.log("--------findNotVisitedNodeInLevel: level=$level")
            return node
        }
        return null
    }

    private fun loadFromLocal(){
    }

    fun nodeCount(): Int {
        var count = 0
        levels.forEach { count += it.size }
        return count
    }

    private suspend fun waitOrTimeoutDeferred(map: LinkedHashMap<String, CompletableDeferred<Any?>>,
                                      df: CompletableDeferred<Any?>, node: ThatPrimaryNode) {
        withTimeoutOrNull(5000) {
            df.await()
        }
        //mLog.log("----find node timeout--df is active: ${df.isActive}")
        if (df.isActive) {
            df.complete(null)
            completeDeferred(map, node)
        }
    }

    private fun completeDeferred(map: LinkedHashMap<String, CompletableDeferred<Any?>>, node: ThatPrimaryNode) {
        completeDeferred(map, node.ip, node.port)
    }

    private fun completeDeferred(map: LinkedHashMap<String, CompletableDeferred<Any?>>,
                                 ip: String, port: Int) {
        val df = map.remove(getKey(ip, port)) ?: return
        df.complete(null)
    }

    private fun findNode(ctx: ChannelHandlerContext?): ThatPrimaryNode? {
        if (ctx == null) {
            return null
        }
        for (lv in levels) {
            if (lv.isEmpty()) {
                continue
            }
            for (n in lv) {
                if (n.ctx == ctx) {
                    return n
                }
            }
        }
        return null
    }

    private fun findNode(node: ThatPrimaryNode): ThatPrimaryNode? {
        return findNode(node.ip, node.port)
    }

    private fun findNode(ip: String, port: Int): ThatPrimaryNode? {
        for (i in levels.indices) {
            val n = findNodeInLevel(i, ip, port)
            if (n != null) {
                return n
            }
        }
        return null
    }

    private fun findNodeInLevel(lv: Int, ip: String, port: Int): ThatPrimaryNode? {
        for (n in levels[lv]) {
            if (n.isSameKey(ip, port)) {
                mLog.log("--------findNode: $n")
                return n
            }
        }
        return null
    }

    fun findNodeByAddr(addr: ByteArray): ThatPrimaryNode? {
        //is this faster than traversal?
        val lv = levelOf(addr)
        for (node in levels[lv]) {
            if (node.kid.contentEquals(addr)) {
                return node
            }
        }
        return null
    }

    fun findRandomNodes(toList: MutableList<ThatPrimaryNode>, max: Int) {
        val tmpList = LinkedList<ThatPrimaryNode>()
        for (lv in levels) {
            tmpList.addAll(lv)
        }
        if (tmpList.size <= max) {
            toList.addAll(tmpList)
            return
        }
        val rand = Random(System.nanoTime())
        while (toList.size < max) {
            toList.add(tmpList.removeAt(rand.nextInt(tmpList.size)))
        }
    }

    /**
     * @return is full
     */
    private fun addLevelToList(lv: Int, list: LinkedList<ThatPrimaryNode>, exclude: ThatPrimaryNode): Boolean {
        if (list.size >= LEVEL_NODE_COUNT) {
            return true
        }
        if (lv < 0 || lv >= levels.size || levels[lv].size == 0) {
            return false
        }
        for (node in levels[lv]) {
            if (node.isSameKey(exclude)) {
                continue
            }
            list.add(node)
            if (list.size >= LEVEL_NODE_COUNT) {
                break
            }
        }
        return list.size >= LEVEL_NODE_COUNT
    }

    @Subscribe
    fun onActive(event: ActiveEvent) {
        mLog.log("--------onActive(): $event    local: ${event.ctx?.channel()?.localAddress()}")
        if (isMe(event)) {
            mLog.log("----------------onActive(), self connection")
            return
        }

        //send ping
        mLog.log("--------send ping to $event")
        event.sendCmd(createPingCmd())
    }

    @Subscribe
    fun onInactive(event: InactiveEvent) {
        mLog.log("--------onInactive(): $event")
        delete(event)
    }

    @Subscribe
    fun onPing(event: PingEvent) {
        //deal event
        mLog.log("--------onPing: $event")
        if (isMe(event)) {
            mLog.log("----------------onPing(), self ping")
            return
        }
        if (event.kid.size != ADDRESS_SIZE) {
            mLog.err("----error kid: ${ByteUtil.toHexString(event.kid)}")
            return
        }
        if (!event.isPrimary) { //minor
            if (Env.noMinor) {
                event.ctx?.close()
                return
            }
            val node = findNodeInMinor(event)
            mLog.log("----get minor node: $event")
            if (node == null) { //add to list
                minorList.add(event)
            } else { //update
                node.kid = event.kid
                node.ctx = event.ctx
            }
            minorChannels.add(event.ctx?.channel())
            return
        }
        try {
            val node = findNode(event)
            if (node != null) { //in KBucket, update
                node.kid = event.kid
                node.ctx = event.ctx
                return
            }
            add(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Subscribe
    fun onFindNode(event: FindNodeEvent) {
        //deal event
        mLog.log("--------onFindNode, is request: ${event.kid.isNotEmpty()}  event=$event")
        if (event.kid.isNotEmpty()) { //request
            val lv = levelOf(event.kid)
            for (node in levels[lv]) {
                if (node.isSameKey(event)) {
                    continue
                }
                if (node.kid.contentEquals(event.kid)) { //found, give the node
                    mLog.log("----onFindNode found")
                    event.sendCmd(createFindNodeCmd(EMPTY_BYTE_ARRAY, event))
                    return
                }
            }
            //not found, give 8 nodes
            val list = LinkedList<ThatPrimaryNode>()
            if (!addLevelToList(lv, list, event)) {
                for (i in 1 until levels.size) {
                    if (addLevelToList(lv - i, list, event)) {
                        break
                    }
                    if (addLevelToList(lv + i, list, event)) {
                        break
                    }
                }
            }
            mLog.log("----onFindNode give 8 notes")
            event.sendCmd(createFindNodeCmd(EMPTY_BYTE_ARRAY, *list.toTypedArray()))
            return
        }
        //response
        try {
            for (node in event.nodeList) {
                addNode2Wait(node)
            }
        } catch (e: Exception) {
        }
        //remove deferred
        completeDeferred(expandDFMap, event.ip, event.port)
    }


    //=========================broadcast=========================
    fun recordBroadMsg(uuid: String) {
        broadcastMessages[uuid] = curTime()
    }

    fun checkBroadOvertime() {
        val time = curTime()
        for (key in broadcastMessages.keys()) {
            if (time - (broadcastMessages[key] ?: Long.MAX_VALUE) > 2 * 60 * 60 * 1000) {
                broadcastMessages.remove(key)
            }
        }
    }

    fun hasReceived(uuid: String): Boolean {
        return broadcastMessages.containsKey(uuid)
    }

    fun sendBroadcast(data: TerabitData, uuid: String? = null) {
        if (nodeCount() == 0) {
            return
        }
        val cmd = createTerabitDataCmd(data)
        val u = cmd.addUuid(uuid)
        mLog.log("----sendBroadcast(): $data     uuid=$u")
        recordBroadMsg(u)
        primaryChannels.writeAndFlush(cmd)
    }


    //=========================minor node=========================
    private fun findNodeInMinor(it: ThatPrimaryNode): ThatPrimaryNode? {
        for (node in minorList) {
            if (node.isSameKey(it)) {
                return node
            }
        }
        return null
    }

    fun sendBroadcastToMinor(data: TerabitData) {
        if (Env.noMinor) {
            return
        }
        mLog.log("----sendBroadcastToMinor(): $data")
        if (minorList.isEmpty()) {
            return
        }
        minorChannels.writeAndFlush(createTerabitDataCmd(data))
    }

    fun sendBroadcastToMinor(cmd: RemoteCmd) {
        if (Env.noMinor) {
            return
        }
        if (minorList.isEmpty()) {
            return
        }
        minorChannels.writeAndFlush(cmd)
    }
}
