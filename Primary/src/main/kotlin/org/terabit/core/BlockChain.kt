package org.terabit.core

import org.ethereum.util.ByteUtil
import org.terabit.common.*
import org.terabit.core.Env.getMethod1Gas
import org.terabit.core.Env.getMinerShouldReward
import org.terabit.core.base.getListRootHash
import org.terabit.core.state.StateDb
import org.terabit.db.*
import org.terabit.primary.PrimaryConfig
import org.terabit.primary.impl.actStore
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

val myAccount = Account()

//put it here temporarily
private val receipts = ArrayList<TransactionReceipt>()
private val activations = ArrayList<ActivationInfo>()

private val godAddress = arrayOf(
        "f678f67e38871e6209dccafe2d2a8c52b9bda3ea2373faf43efed0c44ccd56ae",
        "479725bad084247f3a5d5b709b4135d6a7d8f5d621e705be65eb81813da915a5",
        "501c876cd92541d93b87a74aec86ec9e95f164e70ce6f7b837551db73962486c",
        "aac5594c932ba58194047a311556380c08a333314d5755c58210d0b398e77b21",
        "a0a9a95894a77a187e9b9eefb37b6f0bf3afa27a7b04b176a3d8cd936cc9103a",
        "f02c3ecdc3df62cc62494398a69e0c45730d9539760952d11e138bdc7b6052d8",
        "dbfeb1a1abde5121a764120a886eb0ebff76ab9e72b21d9c4bbd6232eb4ae6bf",
        "fc9dcc6bbf885c4c2fd94b77c1eef6d73dbd6bef7f8e91b385b0d5335ce1ecb1",
        "4e79f96eb3d3a913e4800bd9ef01edb68be4338f25bc41eb63df83bb041b0588",
        "28106c79589113463ed330159ed6b47c444dd5751aea8a4269349b97076cd220"
)

@Synchronized
fun createNewBlock(parent: FinalBlock, txList: List<Transaction>, gasLimit: Long, extra: String): FinalBlock {
    var time = curSecond()
    if (time <= parent.header.timestamp) time = parent.header.timestamp + 1
    val difficulty = calculateDifficulty(parent.header.difficulty, time - parent.header.timestamp)
    return createNewBlock(parent, txList, time, difficulty, gasLimit, extra)
}

@Synchronized
fun createNewBlock(parent: FinalBlock,
                   txList: List<Transaction>,
                   time: Long,
                   difficulty: Long,
                   gasLimit: Long,
                   extra: String
): FinalBlock {

    return FinalBlock(parent.getHash(), myAccount.getAddrBytes(), time, parent.header.height + 1,
            difficulty, gasLimit, extra.toByteArray(), txList)
}

fun checkTx(tx: Transaction, sender: StateObject, block: FinalBlock? = null): TxCode {
    tx.gasUsed = GAS_TRANSACTION

    if (tx.nonce != sender.nonce + 1) { //error nonce
        return TxCode.NONCE_ERROR
    }
    if (tx.amount + GAS_TRANSACTION * tx.gasPrice > sender.balance) { //insufficient balance
        return TxCode.INSUFFICIENT_BALANCE
    }

    val blockLimit = block?.header?.gasLimit ?: Env.getBlockGasLimit()
    if (tx.gasLimit > blockLimit) { //gas limit set too large
        return TxCode.LARGE_GAS_LIMIT
    }
    //already in block, no more check
    if (block == null && tx.gasPrice < PrimaryConfig.gasPrice) { //this miner do not process transactions below this price
        return TxCode.LOWER_GAS_PRICE
    }

    //under normal circumstances, the transaction will succeed here
    if (tx.to.isEmpty()) { //is contract
        if (tx.ktVersion() != CONTRACT_VERSION_INIT) {
            return TxCode.FAILED
        }
        when (tx.ktMethod()) {
            CONTRACT_METHOD_APPLY_FOR_ACTIVATION -> {
                val size = CONTRACT_VERSION_LENGTH + CONTRACT_METHOD_LENGTH
                if (tx.data.size != size) {
                    return TxCode.DATA_ERROR
                }
                //already in block, no more check gas price
                if (block == null && tx.gasPrice < PrimaryConfig.gasPriceMethod1) {
                    return TxCode.LOWER_GAS_PRICE
                }
                val gas = getMethod1Gas()
                if (gas * tx.gasPrice > sender.balance ) {
                    return TxCode.INSUFFICIENT_GAS
                }
                tx.gasUsed = gas
            }
            else -> return TxCode.INVALID_METHOD
        }
        return TxCode.SUCCESS
    }

    return TxCode.SUCCESS
}

//return the gas cost to miner
fun applyTx(db: StateDb, block: FinalBlock, tx: Transaction, gasUsed: Long): Long {
    val from = tx.getFrom()
    val sender = db.getStateObject(from) ?: StateObject(from, 0, 0)

    val result = checkTx(tx, sender, block)
    tx.result = result
    sender.nonce++

    var cost = tx.gasUsed * tx.gasPrice
    //println("----------applyTx: ${tx.result}")
    if (tx.result != TxCode.SUCCESS) {
        cost = min(sender.balance, cost)
        db.modifyCoin(sender, -cost)
        return cost
    }

    if (gasUsed + tx.gasUsed > block.header.gasLimit) {
        return 0
    }

    val receiver = if (tx.to.isEmpty()) null else db.findOrCreateAccount(tx.to)
    if (receiver == null) {
        db.modifyCoin(sender, -cost)
    } else {
        db.modifyCoin(sender, -tx.amount - cost)
        db.modifyCoin(receiver, tx.amount)
    }

    return cost
}

fun applyBlock(parent: FinalBlock, block: FinalBlock): StateDb {
    println("------------------------------------------------------applyBlock()")
    receipts.clear()
    activations.clear()

    val db = StateDb.load(parent.header.stateRoot)

    var gasUsed = 0L
    var gasCost = 0L
    if (block.txList.size > 0) {
        var index = 0
        for ((i,tx) in block.txList.withIndex()) {
            val lastGas = block.header.gasLimit - gasUsed
            //not enough gas for one transaction
            if (lastGas < GAS_TRANSACTION || (tx.to.isEmpty() && lastGas < GAS_TX_CREATE)) {
                break
            }
            index = i
            val cost = applyTx(db, block, tx, gasUsed)
            if (cost == 0L || tx.result == TxCode.NONCE_ERROR) {
                index = i - 1
                break
            }
            gasUsed += tx.gasUsed
            gasCost += cost
            receipts.add(TransactionReceipt(block.getHeight(), i, tx.getHash(), tx.result, tx.gasUsed.toLong()))

            if (tx.to.isNotEmpty()) {
                continue
            }
            //contract
            when (tx.ktMethod()) {
                CONTRACT_METHOD_APPLY_FOR_ACTIVATION -> {
                    var info = actStore.findActivation(tx.getFrom())
                    if (info == null || info.activations.isEmpty()) {
                        info = ActivationInfo(block, i)
                    } else {
                        val act = info.activations.last
                        val start = if (act.timeEnd == 0L) block.header.timestamp else act.timeEnd
                        info.activations.add(Activation(start,
                                start + CONTRACT_ACTIVATION_VALID_PERIOD, block.getHeight(), i))
                    }
                    activations.add(info)
                }
            }
        }
        if (index < block.txList.size - 1) {
            println("--------too many transactions, queue size=${block.txList.size}, package size=${index + 1}")
            val list = block.txList.subList(0, index + 1)
            val tempList = LinkedList<Transaction>()
            tempList.addAll(list)
            block.setTxs(tempList)
        }
    }
    block.header.gasUsed = gasUsed
    block.header.gasCost = gasCost
    block.header.txRoot = getListRootHash(block.txList)

    val miner = db.findOrCreateAccount(block.header.miner)
    db.modifyCoin(miner, getMinerShouldReward(block.getHeight()) + gasCost)
    return db
}

fun saveBlock(block: FinalBlock, blockStore: BlockStore, txStore: TransactionStore,
              getStateDb:(()->StateDb?)? = null): Boolean {
    if (block.getHeight() == 0L) {
        blockStore.save(block)
        //genesis block, hardcode the god accounts for activation
        for (addr in godAddress) {
            actStore.saveActivation(ActivationInfo(ByteUtil.hexStringToBytes(addr), 0, 0,
                    block.header.timestamp, 0, 0))
        }
        Env.update(block)
        return true
    }
    val parent = Env.bestBlock ?: return false
    val stateDb = getStateDb?.let { it() } ?: applyBlock(parent, block)
    if (!stateDb.getRoot().contentEquals(block.header.stateRoot)) {
        println("----------------------------------saveBlock--error")
        return false
    }
    stateDb.flush()
    println("----------------------------------save block--------------------------------")
    //stateDb.showNode()
    blockStore.save(block)
    //Receipts
    for (rpt in receipts) {
        txStore.saveTx(rpt)
    }
    //activation
    for (act in activations) {
        actStore.saveActivation(act)
    }
    Env.update(block)
    return true
}

fun calculateDifficulty(last: Long, timeSpan: Long): Long {
    return DEFAULT_DIFFICULTY
}