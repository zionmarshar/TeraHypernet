package org.terabit.db

import org.terabit.common.bytes
import org.terabit.core.*
import org.ethereum.datasource.DataSourceArray
import org.terabit.common.toLong
import org.terabit.core.Transaction
import org.terabit.core.base.decodeList
import org.terabit.core.base.encodeList
import java.io.Closeable

class BlockStore(dbName: String): Closeable {
    private val db = LevelDbDataSource(dbName)
    private val dataArray = DataSourceArray(db)

    fun load() {
    }

    fun save(block: FinalBlock) {
        val height = block.getHeight()
        val hash = block.getHash()
        //hash
        db.put(getBlockHashKey(height), hash)
        //height
        db.put(getBlockHeightKey(hash), height.bytes())
        //header
        db.put(getHeaderKey(height, hash), block.header.encode())
        //transaction
        db.put(getBlockTranKey(height, hash), encodeList(block.txList))
        //vote
        db.put(getBlockVoteKey(height, hash), encodeList(block.votes))

        //set size, use int temporarily
        dataArray.setSize(height.toInt() + 1)
    }

    @Synchronized
    fun getBestBlock(): FinalBlock? {
        var maxLevel: Long = getMaxNumber()
        if (maxLevel < 0) return null
        var bestBlock: FinalBlock? = getChainBlockByHeight(maxLevel)
        if (bestBlock != null) return bestBlock

        while (bestBlock == null) {
            --maxLevel
            bestBlock = getChainBlockByHeight(maxLevel)
        }
        return bestBlock
    }

    fun setHeight(height: Long) {
        dataArray.setSize(height.toInt())
    }

    @Synchronized
    fun getMaxNumber(): Long {
        var bestIndex = 0L
        if (dataArray.size > 0) {
            bestIndex = dataArray.size.toLong()
        }
        return bestIndex - 1L
    }

    @Synchronized
    fun getChainBlockByHeight(height: Long): FinalBlock? {
        if (height >= dataArray.size) {
            return null
        }
        //block height is equal to the index of chain
        val hash = dataArray[height.toInt()] ?: return null
        val header = db.get(getHeaderKey(height, hash))
        val tran = db.get(getBlockTranKey(height, hash))
        val vote = db.get(getBlockVoteKey(height, hash))
        return FinalBlock(header, tran, vote)
    }

    fun getChainHeaderByHeight(height: Long): BlockHeader? {
        if (height >= dataArray.size) {
            return null
        }
        val hash = dataArray[height.toInt()] ?: return null
        val header = db.get(getHeaderKey(height, hash))
        return BlockHeader(header)
    }

    fun getHeightByHash(hash: ByteArray): Long {
        val bytes = db.get(getBlockHeightKey(hash)) ?: return -1
        return bytes.toLong()
    }

    fun getHashByHeight(height: Long): ByteArray? {
        return db.get(getBlockHashKey(height))
    }

    fun getTx(blockHash: ByteArray, txIndex: Int): Transaction? {
        if (txIndex < 0) {
            return null
        }

        val bytes = db.get(getBlockHeightKey(blockHash)) ?: return null
        val height = bytes.toLong()

        val tran = db.get(getBlockTranKey(height, blockHash))
        val list = decodeList(tran, Transaction::class.java)
        if (txIndex >= list.size) {
            return null
        }
        return list[txIndex]
    }

    override fun close() {
        db.close()
    }
}