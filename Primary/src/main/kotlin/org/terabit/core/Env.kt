package org.terabit.core

import org.terabit.common.*
import org.terabit.core.state.StateDb
import org.terabit.db.Activation
import org.terabit.primary.impl.actStore

object Env {
    var isTest = true
    var noMinor = true  //remove minor node mode

    var isRun = true

    var bestBlock: FinalBlock? = null
    var curState: StateDb? = null

    val monitorLock = Object()
    var monitorCount = 0
    const val MONITOR_COUNT_LIMITED = 10

    val txPool = TransactionPool()

    private var blockGasLimit = BLOCK_GAS_LIMIT_MIN

    fun update(block: FinalBlock) {
        bestBlock = block
        curState = StateDb.load(block.header.stateRoot)

        changeGasLimit()

        txPool.recheck()

        synchronized(monitorLock) {
            monitorCount = 0
            monitorLock.notifyAll()
        }
    }

    fun getMinerShouldReward(blockHeight: Long): Long {
        if (isTest) {
            return 100000
        }
        if (blockHeight <= 0) {
            return 0L
        }
        val index = ((blockHeight - 1) / BLOCK_REWARD_PHASE).toInt()
        if (index >= BLOCK_REWARD_COUNT.size) {
            return 0L
        }
        return (BLOCK_REWARD_COUNT[index] * TERA_COIN).toLong()
    }

    private fun changeGasLimit() {
        val block = bestBlock ?: return
        blockGasLimit = block.header.nextGasLimit()
    }

    fun getBlockGasLimit(): Long {
        return blockGasLimit
    }

    fun getCurrentKtVersion(): Short {
        return CONTRACT_VERSION_INIT
    }

    fun getKtMethod1Count(): Int {
        return 1
    }

    //bytes count: key+value, and create contract gas
    fun getMethod1Gas(): Int {
        return (ADDRESS_SIZE + CONTRACT_METHOD_1_DATE_LENGTH) * GAS_V1_SIMPLE_STORAGE + GAS_TX_CREATE
    }

    fun getExpectGasPrice(): Long {
        return 2
    }

    private var myActivation: Activation? = null
    fun updateActivation() {
        val actInfo = actStore.findActivation(myAccount.getAddrBytes())
        this.myActivation = actInfo?.activations?.lastOrNull()
    }
    fun hasActivation(checkTime: Long): Boolean {
        return myActivation?.isValid(checkTime) == true
    }
}