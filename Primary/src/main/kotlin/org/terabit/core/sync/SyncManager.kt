package org.terabit.core.sync

import kotlinx.coroutines.*
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_BRANCH
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_ERROR
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_EXTEND
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_SAME
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_SHORT
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.events.*
import org.terabit.primary.getKey
import org.terabit.primary.impl.*
import org.greenrobot.eventbus.Subscribe
import org.terabit.core.*
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min

private const val SYNC_NODE_COUNT = 2
private const val SYNC_HEADER_COUNT = 5

class SyncManager {

    var isSync = false
    private var state = 0

    private val nodes = LinkedList<ThatPrimaryNode>()
    private val dfMap = LinkedHashMap<String, CompletableDeferred<Any?>>()
    private val headerResults = ConcurrentLinkedQueue<SyncHeaderResult>()

    private val branchHeaderList = ArrayList<SyncHeader>()
    private var branchHeight = 0L
    private var branchDf = CompletableDeferred<List<SyncHeader>?>()

    private var blockHeight = 0L
    private var blockDf = CompletableDeferred<SyncBlockResultEvent?>()

    private fun clear() {
        isSync = false
        state = 0
        nodes.clear()
        dfMap.clear()
        headerResults.clear()

        branchHeaderList.clear()
    }

    fun startSync(): Boolean {
        clear()
        isSync = true
        val result = sync()
        clear()
        return result
    }

    private fun sync(): Boolean {
        state = 0
        println("-------------------------------------------------sync start")
        kBucket.findRandomNodes(nodes, SYNC_NODE_COUNT)
        println("----sync nodes.size=${nodes.size}")
        if (nodes.size < SYNC_NODE_COUNT) {
            return false
        }

        val block = blockStore.getBestBlock() ?: return false
        println("----sync best block: ${block.getHeight()}")

        //check header
        runBlocking {
            state = 1
            checkHeader(block)
            state = 2
        }

        //check blocks and determine node
        println("----sync headerResults.size: ${headerResults.size}")
        val result = determineResult() ?: return false
        if (result.result == RT_SAME) {
            return true
        }
        if (result.result == RT_BRANCH) { //find branch node
            if (block.getHeight() < result.headerList.last().height) {
                branchHeight = block.getHeight()
            } else {
                branchHeaderList.addAll(result.headerList)
            }
            val header = runBlocking {
                findBranchBlock(result.node)
            }
            if (header == null || header.height > block.getHeight()) {
                return false
            }
            blockStore.setHeight(header.height)
        }
        state = 3
        var newBlock = blockStore.getBestBlock() ?: return false
        println("----sync newBlock: ${newBlock.getHeight()}")

        //Synchronize
        return runBlocking {
            var failCount = 0
            while (true) {
                blockHeight = newBlock.getHeight() + 1
                blockDf = CompletableDeferred()
                println("----sync block height: $blockHeight")
                result.node.sendCmd(createSyncBlockCmd(blockHeight))
                withTimeoutOrNull(5000) {
                    blockDf.await()
                }
                println("----sync blockDf.isActive: ${blockDf.isActive}")
                if (blockDf.isActive) {
                    blockDf.complete(null)
                }
                val blockResult = try {blockDf.getCompleted()} catch (e: Exception) {null}
                println("----sync blockResult: ${blockResult?.code}")
                if (blockResult == null || blockResult.code == 1 || blockResult.block == null
                        || blockResult.block.getHeight() != blockHeight
                        || !blockResult.block.verify(newBlock) || !saveBlock(blockResult.block, blockStore, txStore)) {
                    failCount++
                    if (failCount > 2) {
                        break
                    }
                    continue
                }
                showBlock(blockResult.block)
                if (blockResult.code == 100) {
                    return@runBlocking true
                }
                newBlock = blockResult.block
            }
            false
        }
    }

    private suspend fun checkHeader(block: FinalBlock) {
        val cmd = createSyncHeaderCmd(SyncHeader(block.header))
        val jobList = LinkedList<CompletableDeferred<Any?>>()
        for (node in nodes) {
            val df = CompletableDeferred<Any?>()
            dfMap[node.getKey()] = df
            jobList.add(df)

            node.sendCmd(cmd)
            println("--------send cmd $cmd to $node")

            GlobalScope.launch {
                withTimeoutOrNull(5000) {
                    df.await()
                }
                if (df.isActive) {
                    completeDeferred(dfMap, node)
                }
            }
        }
        if (jobList.isNotEmpty()) {
            awaitAll(*jobList.toTypedArray())
        }
    }

    @Synchronized
    private fun completeDeferred(map: LinkedHashMap<String, CompletableDeferred<Any?>>, node: ThatPrimaryNode): Boolean {
        val df = map.remove(getKey(node.ip, node.port)) ?: return false
        df.complete(null)
        return true
    }

    private fun determineResult(): SyncHeaderResult? {
        println("----determineResult-----------------------")
        if (headerResults.size < 2) {
            return null
        }
        var best: SyncHeaderResult? = null
        for (result in headerResults) {
            println("----determineResult--result=$result")
            if (best == null) {
                best = result
                continue
            }
            if (result.result == RT_SAME) {
                if (best.result == RT_BRANCH) {
                    best = result
                    continue
                }
                continue //same and extend
            }
            if (result.result == RT_EXTEND) {
                if (best.result != RT_EXTEND) { //same and branch
                    best = result
                    continue
                }
                //all is extend
                best = compareSyncHeaderResult(best, result)
                continue
            }
            //result is branch
            if (best.result != RT_BRANCH) {
                continue
            }
            //all is branch
            best = compareSyncHeaderResult(best, result)
        }

        println("----determineResult-------------------best=$best")
        return best
    }

    private fun compareSyncHeaderResult(shr1: SyncHeaderResult, shr2: SyncHeaderResult): SyncHeaderResult {
        val newHeader1 = shr1.headerList.first()
        val newHeader2 = shr2.headerList.first()
        val minNewestHeight = if (newHeader1.height == newHeader2.height) {
            if (newHeader1.getHash().contentEquals(newHeader2.getHash())) {
                return shr1
            }
            newHeader1.height
        } else {
            min(newHeader1.height, newHeader2.height)
        }
        val maxLowHeight = max(shr1.headerList.last().height, shr2.headerList.last().height)
        if (maxLowHeight <= minNewestHeight) {
            for (i in maxLowHeight..minNewestHeight) {
                val h1 = shr1.findSyncHeader(i) ?: return shr2
                val h2 = shr2.findSyncHeader(i) ?: return shr1
                if (h1.getHash().contentEquals(h2.getHash())) {
                    continue
                }
                return if (h1.minorRound > h2.minorRound) shr2 else shr1
            }
        }
        return if (newHeader1.height > newHeader2.height) shr1 else shr2
    }

    private suspend fun findBranchBlock(node: ThatPrimaryNode): SyncHeader? {
        var overtimeCount = 0
        while (true) {
            if (branchHeaderList.size > 0) {
                for (h in branchHeaderList) {
                    val header = blockStore.getChainHeaderByHeight(h.height) ?: continue
                    if (h.getHash().contentEquals(header.getHash())) {
                        println("-------------findBranchBlock----find height: ${h.height}")
                        return h
                    }
                }
                branchHeight = branchHeaderList.last().height - 1
                if (branchHeight <= 0) {
                    println("-------------findBranchBlock----not find:")
                    return null
                }
                branchHeaderList.clear()
            }
            branchDf = CompletableDeferred()
            println("-------------findBranchBlock----send cmd branchHeight=$branchHeight")
            node.sendCmd(createListHeaderCmd(branchHeight, 10))
            withTimeoutOrNull(5000) {
                branchDf.await()
            }
            if (branchDf.isActive) {
                branchDf.complete(null)
            }
            val listResult = try {branchDf.getCompleted()} catch (e: Exception) {null}
            if (listResult == null || listResult.isEmpty()) {
                overtimeCount++
                if (overtimeCount > 2) {
                    return null
                }
                continue
            }
            branchHeaderList.addAll(listResult)
        }
    }

    private fun getHeaders(remoteHeader: SyncHeader, list: LinkedList<SyncHeader>): Int {
        val lastBlock = blockStore.getBestBlock() ?: return RT_ERROR
        if ((lastBlock.getHeight() == remoteHeader.height && lastBlock.getHash().contentEquals(remoteHeader.getHash()))) {
            return RT_SAME
        }
        list.add(SyncHeader(lastBlock.header))
        //longer than my chain, or same as me
        if (lastBlock.getHeight() < remoteHeader.height) {
            return RT_SHORT
        }

        val block = blockStore.getChainBlockByHeight(remoteHeader.height) ?: return RT_ERROR
        val result = if (block.getHash().contentEquals(remoteHeader.getHash())) RT_EXTEND else RT_BRANCH

        for (i in 1 until SYNC_HEADER_COUNT) {
            val height = lastBlock.getHeight() - i
            if (height < 0) {
                break
            }
            val header = blockStore.getChainHeaderByHeight(height) ?: return RT_ERROR
            list.add(SyncHeader(header))
        }

        return result
    }

    @Subscribe
    fun onSyncHeader(event: SyncHeaderEvent) {
        val list = LinkedList<SyncHeader>()
        val result = getHeaders(event.header, list)
        val cmd = createSyncHeaderResultCmd(result, list)
        println("-------------onSyncHeader()----send cmd=$cmd   to $event")
        event.sendCmd(cmd)
    }

    @Subscribe
    fun onSyncHeaderResult(result: SyncHeaderResult) {
        println("-------------onSyncHeaderResult()----result=$result")
        if (state != 1) {
            return
        }
        if (completeDeferred(dfMap, result.node)) {
            if (result.result == RT_ERROR || result.result == RT_SHORT
                    || (result.result != RT_SAME && result.headerList.size == 0)) {
                return
            }
            headerResults.add(result)
        }
    }

    @Subscribe
    fun onListHeader(event: ListHeaderEvent) {
        println("-------------onListHeader()----height=${event.height}")
        val list = LinkedList<SyncHeader>()
        for (i in 0 until event.count) {
            val height = event.height - i
            if (height < 0) {
                break
            }
            val header = blockStore.getChainHeaderByHeight(height) ?: break
            list.add(SyncHeader(header))
        }
        event.sendCmd(createListHeaderResultCmd(list, event.height))
    }

    @Subscribe
    fun onListHeaderResult(result: ListHeaderResultEvent) {
        println("-------------onListHeaderResult()----height=${result.height}")
        if (state != 2 || result.height != branchHeight) {
            return
        }
        if (branchDf.isActive) {
            branchDf.complete(result.list)
        }
    }

    @Subscribe
    fun onSyncBlock(event: SyncBlockEvent) {
        println("-------------onSyncBlock()----height=${event.height}")
        val block = blockStore.getChainBlockByHeight(event.height)
        val result = when {
            block == null -> 1
            block.getHeight() == blockStore.getMaxNumber() -> 100
            else -> 0
        }
        event.sendCmd(createSyncBlockResultCmd(result, block))
    }

    @Subscribe
    fun onSyncBlockResult(result: SyncBlockResultEvent) {
        println("-------------onSyncBlockResult()----height=${result.block?.getHeight()}")
        if (state != 3 || (result.block != null && result.block.getHeight() != blockHeight)) {
            return
        }
        if (blockDf.isActive) {
            blockDf.complete(result)
        }
    }
}