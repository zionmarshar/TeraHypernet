package org.terabit.db

import org.terabit.core.TransactionReceipt
import org.ethereum.util.RLP

class TransactionStore(dbName: String) {
    private val db = LevelDbDataSource(dbName)

    fun saveTx(txInfo: TransactionReceipt) {
        db.put(txInfo.txHash, txInfo.encode())
    }

    fun findTxReceipt(hash: ByteArray): TransactionReceipt? {
        val bytes = db.get(hash) ?: return null
        return TransactionReceipt(RLP.decodeList(bytes))
    }
}