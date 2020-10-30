package org.terabit.core

import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.common.*
import org.terabit.core.base.TerabitData
import org.terabit.core.base.decodeList
import org.terabit.core.base.encodeList
import org.terabit.core.base.getListRootHash

class FinalBlock(val header: BlockHeader): TerabitData {

    val votes = ArrayList<Vote>()
    val txList = ArrayList<Transaction>()

    constructor(parentHash: ByteArray,
                miner: ByteArray,
                timestamp: Long,
                height: Long,
                difficulty: Long,
                gasLimit: Long,
                extraData: ByteArray,
                txList: List<Transaction>?
    ) : this(BlockHeader(parentHash, miner, timestamp, height, difficulty, gasLimit, extraData)) {
        setTxs(txList)
    }

    constructor(header: ByteArray, tran: ByteArray, vote: ByteArray):this(BlockHeader(header)) {
        setTxs(decodeList(tran, Transaction::class.java))
        setVotes(decodeList(vote, Vote::class.java))
    }

    constructor(rlp: RLPList): this(
            rlp.next().rlpData,
            rlp.next().rlpData,
            rlp.next().rlpData
    )

    override fun getHash(): ByteArray {
        return header.getHash()
    }

    override fun encode(): ByteArray {
        val header = this.header.encode()
        val tran = encodeList(txList)
        val vote = encodeList(votes)
        return RLP.encodeList(header, tran, vote)
    }

    override fun verify(vararg data: Any):Boolean{
        val parent = (if (data.isNotEmpty()) (data[0] as? FinalBlock) else null) ?: return false

        //<<<<<<<<<<--header do not verify, so verify here
        if (parent.header.timestamp >= header.timestamp || parent.header.height + 1 != header.height
                || !parent.getHash().contentEquals(header.parentHash)) {
            return false
        }
        val gasLimitNew = parent.header.nextGasLimit()
        if (header.gasLimit != gasLimitNew) {
            return false
        }
        //>>>>>>>>>>--header do not verify, so verify here

        if (!header.verify()) {
            println("---------block verify-----------header failed")
            return false
        }
        //transaction
        for (t in txList) {
            if (!t.verify()) {
                println("---------block verify-----------tx list failed")
                return false
            }
        }
        //transaction root
        var hash = getListRootHash(txList)
        if (!hash.contentEquals(header.txRoot)) {
            println("---------block verify-----------tx root failed")
            return false
        }
        //vote
        for (v in votes) {
            if (!v.verify(getHash())) {
                println("---------block verify-----------vote failed")
                return false
            }
        }
        //vote root
        hash = getListRootHash(votes)
        if (!hash.contentEquals(header.voteRoot)) {
            println("---------block verify-----------vote root failed")
            return false
        }

        return true
    }

    fun getHeight(): Long {
        return header.height
    }

    fun setTxs(list: List<Transaction>?) {
        txList.clear()
        if (list == null || list.isEmpty()) {
            return
        }

        txList.addAll(list)
    }

    fun setVotes(votes: List<Vote>) {
        this.votes.clear()
        this.votes.addAll(votes)
        this.header.voteRoot = getListRootHash(this.votes)
    }

    override fun toString(): String {
        return "[Block:: height:${getHeight()}, extra:${header.getExtraData()}, minorRound:${header.minorRound}]"
    }
}

fun getBlockTranKey(height: Long, hash: ByteArray): ByteArray {
    return ByteUtil.merge(ByteArray(1) {'t'.toByte()},height.bytes(), hash)
}

fun getBlockVoteKey(height: Long, hash: ByteArray): ByteArray {
    return ByteUtil.merge(ByteArray(1) {'v'.toByte()},height.bytes(), hash)
}

fun getBlockHashKey(height: Long): ByteArray {
    return ByteUtil.merge(ByteArray(1) {'h'.toByte()}, height.bytes(), ByteArray(1) {'n'.toByte()})
}

fun getBlockHeightKey(hash: ByteArray): ByteArray {
    return ByteUtil.merge(ByteArray(1) {'I'.toByte()}, hash)
}