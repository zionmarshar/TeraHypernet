package org.terabit.core

import org.terabit.common.CONTRACT_METHOD_APPLY_FOR_ACTIVATION
import org.terabit.common.Log
import org.terabit.primary.PrimaryConfig
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class TransactionPool {
    private val log = Log("TransactionPool")

    private val readyList = LinkedList<Transaction>() //all ready transaction
    private val lock = ReentrantLock()

    //insert in order of price
    private fun addToReady(tx: Transaction) {
        lock.lock()
        var index = readyList.indexOfFirst { it.gasPrice < tx.gasPrice }
        if (index < 0) {
            index = readyList.size
        }
        readyList.add(index, tx)
        println("------------now tx pool size=${readyList.size}")
        lock.unlock()
    }

    fun newTx(tx: Transaction): TxCode {
        val state = Env.curState ?: return TxCode.FAILED
        val sender = state.findOrCreateAccount(tx.getFrom())

        val result = checkTx(tx, sender)
        if (result != TxCode.SUCCESS) {
            log.log("---------------check error: $result")
            return result
        }

        //check whether to cover the transaction
        val txOld = readyList.find { tx.nonce == it.nonce && tx.pubKey.contentEquals(it.pubKey) }
        if (txOld != null) {
            if (txOld.gasPrice >= tx.gasPrice) { //can not cover
                return TxCode.COVER_ERROR
            }
            //cover
            readyList.remove(txOld)
        }

        if (readyList.size < PrimaryConfig.txMaxCount) { //not full
            addToReady(tx)
            return TxCode.SUCCESS
        }
        //else, list is full
        if (tx.gasPrice < readyList.last.gasPrice) { //price is too low
            return TxCode.LIST_FULL
        }
        addToReady(tx)
        return TxCode.SUCCESS
    }

    fun getAllReady(): LinkedList<Transaction> {
        val list = LinkedList<Transaction>()
        lock.lock()
        list.addAll(readyList)
        lock.unlock()

        //CONTRACT_METHOD_APPLY_FOR_ACTIVATION: can only has be 1
        val state = Env.curState ?: return LinkedList<Transaction>()
        var method1Count = 0
        val newList = list.filter {
            when {
                checkTx(it, state.findOrCreateAccount(it.getFrom())) != TxCode.SUCCESS -> false //check failed
                it.ktMethod() < 0 -> true  //not contract, is a real transaction
                it.ktMethod() != CONTRACT_METHOD_APPLY_FOR_ACTIVATION -> true  //not method 1
                method1Count < Env.getKtMethod1Count() -> { method1Count++; true } //limit the number
                else -> false
            }
        }
        list.clear()
        list.addAll(newList)

        return list
    }

    fun recheck() {
        val state = Env.curState ?: return

        lock.lock()
        readyList.removeAll { checkTx(it, state.findOrCreateAccount(it.getFrom())) != TxCode.SUCCESS }
        lock.unlock()
    }
}