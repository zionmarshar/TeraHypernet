package org.terabit.core

import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.terabit.common.*
import org.terabit.core.Env.hasActivation
import org.terabit.core.Env.txPool
import org.terabit.core.Env.updateActivation
import org.terabit.core.base.TerabitData
import org.terabit.core.state.StateDb
import org.terabit.core.sync.SyncManager
import org.terabit.db.BlockStore
import org.terabit.db.StateObject
import org.terabit.db.TransactionStore
import org.terabit.network.STOP_SEAL
import org.terabit.primary.NodeId
import org.terabit.primary.PrimaryConfig
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.impl.actStore
import org.terabit.primary.impl.createTerabitDataCmd
import java.util.*
import kotlin.concurrent.thread

const val BOOKKEEPER_MIN_COUNT = 2
const val BOOKKEEPER_PROPORTION = 5 //%

const val WORLD_STATE_INIT = 0
const val WORLD_STATE_SYNC = 1
const val WORLD_STATE_CAMPAIGN = 2
const val WORLD_STATE_BOOKKEEPER = 3
const val WORLD_STATE_WAIT_MINOR = 4
const val WORLD_STATE_WAIT_SEAL = 5

private val CAMPAIGN_TIME =    if (Env.isTest) 3000 else 40000 //ms
private val MINOR_TIME =       if (Env.isTest) 3000 else 40000 //ms
private val SEAL_TIME =        if (Env.isTest) 3000 else 30000 //ms
private val BLOCK_TOTAL_TIME = if (Env.isTest) 10000 else 120000 //ms

private val log = Log("WorldManager")

//TODO test
const val showBlockHeader = "showBlock222: "
val txTestAddr: ByteArray = ByteUtil.hexStringToBytes("0a2d552748713c43349e0df51a59e054db3e0f41cefcde2dc09d7045fff868df")
//TODO test

class WorldManager(private val blockStore: BlockStore,
                   private val txStore: TransactionStore,
                   private val bucket: KBucket
) {
    private var worldState = WORLD_STATE_INIT

    private val bookkeeperList = LinkedList<ThatPrimaryNode>()
    private var bookkeeperCount = 0
    private var myCredential: Credential? = null //I am the bookkeeper?
    private var minorEndTime = 0L
    private var myBlock: FinalBlock? = null //block I created and prepared to be added to chain
    private var sealBlock: FinalBlock? = null //I think the best block will be added to chain
    private var sealNode: ThatPrimaryNode? = null
    private var sealEndTime = 0L

    private lateinit var myBlockState: StateDb

    private val votes = LinkedList<Vote>()

    private val syncManager = SyncManager()

    private val bkChannels = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    fun start() {
        EventBus.getDefault().register(syncManager)
        myBlockState = StateDb.load(EMPTY_TRIE_HASH)
        //start a timer to do logic
        log.log("-------------------------------------------block max height=${blockStore.getMaxNumber()}")
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    doTera()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (!Env.isRun) {
                    timer.cancel()
                }
            }
        }, 200, 100)
    }

    private var lastState = -1
    fun doTera() {
        if (worldState != lastState) {
            lastState = worldState
            log.log("---------------------------------------------------------doTera--state=${worldState}")
        }
        when (worldState) {
            WORLD_STATE_INIT -> loadBlockChain()
            WORLD_STATE_SYNC -> syncChain()
            WORLD_STATE_CAMPAIGN -> waitForCampaign()
            WORLD_STATE_BOOKKEEPER -> bookkeeper()
            WORLD_STATE_WAIT_MINOR -> waitMinor()
            WORLD_STATE_WAIT_SEAL -> waitSeal()
            1000 -> { Thread.sleep(60000) } //test
        }
    }

    private fun newRound() {
        myCredential = null
        myBlock = null
        sealBlock = null
        sealNode = null
        synchronized(bookkeeperList) {
            bookkeeperList.clear()
            bookkeeperCount = 0
        }
        votes.clear()
        updateActivation()
    }

    private fun loadBlockChain() {
        blockStore.load()
        var block = blockStore.getBestBlock()
        if (block == null) {
            log.log("--------create genesis block")
            block = GenesisLoader.createGenesis()
            if (!trySaveBlock(block, true)) {
                throw Exception("Can not save block")
            }
        } else {
            Env.update(block)
        }
        showBlock(block)
        worldState = WORLD_STATE_SYNC
    }

    private fun syncChain() {
        //log.log("-------------syncChain()--bucket.nodeCount()=${bucket.nodeCount()}")
        if (bucket.nodeCount() < BOOKKEEPER_MIN_COUNT) {
            return
        }
        if (!syncManager.startSync()) {
            sleep(1000)
            return
        }
        newRound()

        worldState = WORLD_STATE_CAMPAIGN

        //TODO test
//        worldState = 1000
    }

    private fun waitForCampaign() {
        newRound()

        //check activation information
        if (!hasActivation(curSecond())) {
            //TODO test
            if (Env.isTest) {
                val a = Env.curState?.findOrCreateAccount(myAccount.getAddrBytes()) ?: return
                if (a.balance < 200000) {
                    return
                }

                val data = ByteUtil.merge(ByteUtil.shortToBytes(CONTRACT_VERSION_INIT),
                        ByteUtil.shortToBytes(CONTRACT_METHOD_APPLY_FOR_ACTIVATION))
                val tx = createTransaction(1L, 0L, 4, Env.getMethod1Gas().toLong(),
                        myAccount.priKeyBytes, myAccount.pubKeyBytes, EMPTY_BYTE_ARRAY, data)
                bucket.sendBroadcast(tx)
                log.log("---------------------try to get activation!!!")
            }
            sleep(5000)
            return
        }

        val parent = Env.bestBlock ?: return
        // sleep some time, correct the time
        var timeSpan = BLOCK_TOTAL_TIME - (curTime() - parent.header.timestamp * 1000) % BLOCK_TOTAL_TIME - CAMPAIGN_TIME
        if (timeSpan < 0) {
            timeSpan += BLOCK_TOTAL_TIME
        }
        sleep(timeSpan)

        GlobalScope.launch { //calculate my value
            val cred = createRoundHash(myAccount.priKeyBytes, myAccount.pubKeyBytes, credentialDifficulty, parent.getHash()) {
                worldState != WORLD_STATE_CAMPAIGN
            } ?: return@launch
            //I succeeded
            log.log("--------create my round hash, difficulty=$credentialDifficulty, cred=$cred")
            cred.blockHeight = parent.getHeight()
            myCredential = cred
            bucket.sendBroadcast(cred)
            synchronized(bookkeeperList) {
                bookkeeperCount++
            }
        }

        //TODO test transaction
        thread {
            if (!Env.isTest) {
                return@thread
            }
            println("----------------------------stateRoot=${ByteUtil.toHexString(parent.header.stateRoot)}")
            val state = StateDb.load(parent.header.stateRoot)
            //state.showNode()
            val rand = Random(System.nanoTime()).nextInt(100)
            println("----------------------------rand=$rand")
            if (rand < 30) {
                return@thread
            }
            val me = state.getStateObject(myAccount.getAddrBytes()) ?: StateObject(myAccount.getAddrBytes(), 0, 0)
            println("----------------------------me=$me")
            val tx = createTransaction(me.nonce + 1, 40000, 4, GAS_TRANSACTION.toLong(), myAccount.priKeyBytes,
                    myAccount.pubKeyBytes, txTestAddr, EMPTY_BYTE_ARRAY)
            txPool.newTx(tx)
            bucket.sendBroadcast(tx)
        }
        //TODO test transaction

        val lastTime = curTime() + CAMPAIGN_TIME
        while (worldState == WORLD_STATE_CAMPAIGN && Env.isRun) {
            val bucketCount = bucket.nodeCount()
            if (bucketCount < BOOKKEEPER_MIN_COUNT) { //too few nodes
                overRound(false)
                break
            }
            var bkMinCount = bucketCount * BOOKKEEPER_PROPORTION / 100
            if (bkMinCount < BOOKKEEPER_MIN_COUNT) {
                bkMinCount = BOOKKEEPER_MIN_COUNT
            }
            if (bookkeeperCount >= bkMinCount && curTime() > lastTime) {
                log.log("--------goto bookkeeper, bk count=${bookkeeperCount},  min count=$bkMinCount")
                worldState = WORLD_STATE_BOOKKEEPER
                break
            }
            sleep(20)
        }
    }

    private fun bookkeeper() {
        if (myCredential == null) {
            return
        }
        val lastBlock = Env.bestBlock ?: return

        val block = createNewBlock(lastBlock, txPool.getAllReady(), Env.getBlockGasLimit(), PrimaryConfig.jksItemAlias)
        if (!hasActivation(block.header.timestamp)) { //just expired
            overRound(false)
            return
        }
        myBlockState = applyBlock(lastBlock, block)
        block.header.stateRoot = myBlockState.getRoot()
        block.header.minorRound = 1000000000000L
        println("-------------------------------------------------------------------bookkeeper--getHash()")
        println("--------------------------new--stateRoot=${ByteUtil.toHexString(block.header.stateRoot)}")
        block.getHash()
        myBlock = block
        synchronized(votes) {
            this.votes.clear()
        }
        bucket.sendBroadcastToMinor(block.header)
        worldState = WORLD_STATE_WAIT_MINOR
        minorEndTime = curTime() + MINOR_TIME

        //calculate by myself
        thread {
            val sign = createRoundHash(myAccount.priKeyBytes, myAccount.pubKeyBytes, credentialDifficulty,
                    block.getHash()) {
                curTime() > minorEndTime
            } ?: return@thread
            log.log("--------primary createRoundHash in round ${sign.round}")
            sign.isMinorSeal = true
            sign.blockHeight = block.getHeight()
            EventBus.getDefault().post(sign)
        }
    }

    private fun waitMinor() {
        if (myCredential == null) {
            return
        }
        val block = sealBlock
        if (curTime() > minorEndTime) { //minor seal over
            bucket.sendBroadcastToMinor(STOP_SEAL)
            worldState = WORLD_STATE_WAIT_SEAL
            sealEndTime = curTime() + SEAL_TIME

            if (block == null) {
                return
            }
            if (!hasActivation(block.header.timestamp)) {
                overRound(true)
                return
            }

            val signBytes = sign(myAccount.priKeyBytes, block.getHash())
            val vote = Vote(signBytes, myAccount.pubKeyBytes, block.getHeight())
            log.log("--------minor over, min round=${block.header.minorRound}, sealNode=$sealNode")
            if (sealNode == null) { //seal to me
                votes.add(vote)
            } else {
                sealNode?.send(vote)
            }
            checkVotes()
        }
    }

    private fun waitSeal() {
        if (myCredential == null || curTime() < sealEndTime) {
            return
        }
        //no broadcast, maybe error
        log.log("--------seal over, no broadcast")
        overRound(false)
    }

    @Synchronized
    private fun setSealBlock(seal: FinalBlock, node: ThatPrimaryNode?) {
        val oldBlock = sealBlock
        //now, only compare minor round, ignore leader zero count
        log.log("--------setSealBlock()--seal=$seal   old=$oldBlock")
        log.log("--------setSealBlock()--seal=${seal.header.minorRound}   old=${oldBlock?.header?.minorRound}")
        if (oldBlock == null || seal.header.minorRound < oldBlock.header.minorRound) {
            sealBlock = seal
            log.log("----set seal block: ${seal.header.minorRound}")
            sealNode = node
            if (node == null) {
                sendBroadcastToBookkeeper(seal.header)
            }
        }
    }

    private fun checkVotes(vote: Vote? = null) {
        if (worldState < WORLD_STATE_WAIT_MINOR) {
            return
        }

        val block = sealBlock
        if (block == null) {
            overRound(false)
            return
        }
        synchronized(votes) {
            if (vote != null) {
                votes.add(vote)
            }
            log.log("--------checkVotes()--worldState=$worldState  votes.size=${votes.size}  enough=${votes.size > bookkeeperCount * 6 / 10}")
            if (worldState != WORLD_STATE_WAIT_SEAL || votes.size <= bookkeeperCount * 6 / 10) {
                return
            }
            //Be selected
            block.setVotes(votes)

            log.log("--------checkVotes()--worldState=$worldState")
            showBlock(block)

            log.log("--------broadcast block hash: ${ByteUtil.toHexString(block.getHash())}")
            val success = trySaveBlock(block, true)
            overRound(success)
            if (success) {
                bucket.sendBroadcast(block)
            }
        }
    }

    private fun trySaveBlock(block: FinalBlock, isMe: Boolean): Boolean {
        return saveBlock(block, blockStore, txStore) { if (isMe) myBlockState else null }
    }

    private fun sendBroadcastToBookkeeper(data: TerabitData) {
        if (bookkeeperList.isEmpty()) {
            return
        }

        synchronized(bookkeeperList) {
            try {
                for (bk in bookkeeperList) {
                    val ch = bk.ctx?.channel() ?: continue
                    bkChannels.add(ch)
                }
                bkChannels.writeAndFlush(createTerabitDataCmd(data))
            } finally {
                bkChannels.clear()
            }
        }
    }

    private var isTest = false
    private fun overRound(success: Boolean) {
        worldState = if (isTest) {
            if (success) 80 else 99
        } else {
            if (success) WORLD_STATE_CAMPAIGN else WORLD_STATE_SYNC
        }
    }

    @Subscribe
    fun onTerabitData(data: TerabitData) {
        if (syncManager.isSync || worldState < WORLD_STATE_CAMPAIGN) {
            println("--------ON_TERABIT_DATA: $data      ----sync or init, throw away")
            return
        }
        println("--------ON_TERABIT_DATA: $data")
        when (data) {
            is Credential -> {
                val block = (if (data.isMinorSeal) myBlock else Env.bestBlock) ?: return
                if (data.blockHeight != block.getHeight()) {
                    return
                }
                if (!data.verify(block.getHash())) {
                    return
                }

                if (data.isMinorSeal) {
                    println("--------minor seal")
                    //already smaller
                    if (worldState != WORLD_STATE_WAIT_MINOR || data.round >= block.header.minorRound) {
                        return
                    }
                    block.header.minorSign = data.signedBytes
                    block.header.minorPubKey = data.pubKey
                    block.header.minorRound = data.round
                    println("--------recv minor seal, round=${data.round}")
                    setSealBlock(block, null)
                    return
                }

                //check activation information
                if (!actStore.hasActivation(data.getFrom(), curSecond())) {
                    return
                }

                println("--------Credential verify success")
                val node = bucket.findNodeByAddr(data.getFrom()) ?: return
                synchronized(bookkeeperList) {
                    bookkeeperList.add(node)
                    bookkeeperCount++
                }
            }
            is Vote -> {
                val myBlock = sealBlock ?: return
                if (myCredential == null || sealNode != null || data.blockHeight != myBlock.getHeight()
                        || !data.verify(myBlock.getHash())) {
                    return
                }
                //check activation information
                if (!actStore.hasActivation(data.getFrom(), myBlock.header.timestamp)) {
                    return
                }
                checkVotes(data)
            }
            is BlockHeader -> {
                if (myCredential == null) {
                    return
                }
                val block = Env.bestBlock ?: return
                if (!data.verify(block.getHash())) {
                    println("--------BlockHeader verify failed")
                    return
                }
                println("--------BlockHeader verify success")
                val node = bucket.findNodeByAddr(data.miner) ?: return
                setSealBlock(FinalBlock(data), node)
            }
            is FinalBlock -> {
                val parent = Env.bestBlock ?: return
                if (!data.verify(parent)) {
                    println("--------block verify failed")
                    overRound(false)
                    return
                }
                //check activation information
                if (!actStore.hasActivation(data.header.miner, data.header.timestamp)) {
                    return
                }
                println("--------rsv block height: ${data.header.height}")
                println("--------rsv block hash: ${ByteUtil.toHexString(data.getHash())}")
                println("--------rsv block state: ${ByteUtil.toHexString(data.header.stateRoot)}")
                overRound(trySaveBlock(data, false))
                showBlock(data)
            }
            is Transaction -> {
                if (!data.verify()) {
                    return
                }
                txPool.newTx(data)
            }
        }
    }
}

//TODO test method
fun showBlock(block: FinalBlock) {
    if (NodeId != 0 && NodeId != 10000) {
        return
    }
    println("${showBlockHeader}Block{" +
            "height:[${block.getHeight()}], " +
            "hash:[${ByteUtil.firstToHexString(block.getHash())}], " +
            "parent:[${ByteUtil.firstToHexString(block.header.parentHash)}], " +
            "difficulty:[${block.header.difficulty}], " +
            "miner:[${ByteUtil.firstToHexString(block.header.miner)}], " +
            "extra:[${block.header.getExtraData()}], " +
            "tx:[${block.txList.size}], " +
            "vote:[${block.votes.size}], " +
            "state:[${ByteUtil.firstToHexString(block.header.stateRoot)}], " +
            "minorRound:[${block.header.minorRound}]" +
            "}")
}