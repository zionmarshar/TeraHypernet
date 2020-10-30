package org.terabit.core

import org.terabit.common.*
import org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.core.base.TerabitData

class BlockHeader(val parentHash: ByteArray,
                  val miner: ByteArray,
                  val timestamp: Long,  //s
                  val height: Long,
                  val difficulty: Long,
                  val gasLimit: Long,
                  private val extraData: ByteArray
): TerabitData {

    var txRoot: ByteArray = EMPTY_TRIE_HASH
    var stateRoot: ByteArray = EMPTY_TRIE_HASH
    var voteRoot: ByteArray = EMPTY_TRIE_HASH
    private var mixHash = EMPTY_BYTE_ARRAY

    var gasUsed = 0L
    var gasCost = 0L

    var minorSign: ByteArray = EMPTY_BYTE_ARRAY
    var minorPubKey: ByteArray = EMPTY_BYTE_ARRAY
    var minorRound = 0L

    constructor(rlpList: RLPList):this(
            rlpList.next().rlpData,
            rlpList.next().rlpData,
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData
    ) {
        mixHash = rlpList.next().rlpData
        txRoot = rlpList.next().rlpData
        stateRoot = rlpList.next().rlpData
        voteRoot = rlpList.next().rlpData
        gasUsed = rlpList.next().rlpData.toLong()
        gasCost = rlpList.next().rlpData.toLong()
        minorSign = rlpList.next().rlpData
        minorPubKey = rlpList.next().rlpData
        minorRound = rlpList.next().rlpData.toLong()
    }

    constructor(encoded: ByteArray):this(RLP.decodeList(encoded))

    fun getExtraData(): String {
        if (extraData.isEmpty()) {
            return ""
        }
        try {
            return extraData.toString(charset("utf-8"))
        } catch (e: Exception) {
        }
        return ""
    }

    fun nextGasLimit(): Long {
        val percent = gasUsed * 100 / gasLimit.toDouble()
        return if (percent > BLOCK_GAS_LIMIT_TRIGGER_UPPER) {
            gasLimit + BLOCK_GAS_LIMIT_STEP
        } else if (percent < BLOCK_GAS_LIMIT_TRIGGER_LOWER && gasLimit > BLOCK_GAS_LIMIT_MIN) {
            gasLimit - BLOCK_GAS_LIMIT_STEP
        } else {
            gasLimit
        }
    }

    private fun hash() {
        if (mixHash.isNotEmpty()) {
            return
        }
        mixHash = createHash()
    }

    private fun createHash(): ByteArray {
        return getSha256(parentHash, miner, timestamp.bytes(), height.bytes(), extraData, txRoot, stateRoot)
    }

    override fun getHash(): ByteArray {
        hash()
        return mixHash
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(parentHash),
                RLP.encodeElement(miner),
                RLP.encodeElement(timestamp.bytes()),
                RLP.encodeElement(height.bytes()),
                RLP.encodeElement(difficulty.bytes()),
                RLP.encodeElement(gasLimit.bytes()),
                RLP.encodeElement(extraData),
                //The following data must be put to the end
                RLP.encodeElement(mixHash),
                RLP.encodeElement(txRoot),
                RLP.encodeElement(stateRoot),
                RLP.encodeElement(voteRoot),
                RLP.encodeElement(gasUsed.bytes()),
                RLP.encodeElement(gasCost.bytes()),
                RLP.encodeElement(minorSign),
                RLP.encodeElement(minorPubKey),
                RLP.encodeElement(minorRound.bytes())
        )
    }

    //header only verify the basic data
    override fun verify(vararg data: Any):Boolean{
        if (extraData.size > BLOCK_EXTRA_DATA_SIZE || miner.size != ADDRESS_SIZE
                || gasLimit < BLOCK_GAS_LIMIT_MIN || gasLimit > BLOCK_GAS_LIMIT_MAX
                || gasUsed > gasLimit || difficulty < DEFAULT_DIFFICULTY) {
            return false
        }
        //verify hash
        val hash = createHash()
//        println("----header hash=${ByteUtil.toHexString(hash)}")
//        println("----header mixHash=${ByteUtil.toHexString(mixHash)}")
        if (!hash.contentEquals(mixHash)) {
            return false
        }
        //verify minor round and hash
        return verifyRoundHash(minorSign, minorPubKey, minorRound, difficulty, mixHash)
    }

    fun println() {
        println("--parent = ${ByteUtil.toHexString(parentHash)}")
        println("--hash   = ${ByteUtil.toHexString(mixHash)}")
        println("--height = $height")
        println("--miner  = ${ByteUtil.toHexString(miner)}")
        println("--state  = ${ByteUtil.toHexString(stateRoot)}")
        println("--txRoot = ${ByteUtil.toHexString(txRoot)}")
        println("--time   = $timestamp")
        println("--diff   = $difficulty")
        println("--limit  = $gasLimit")
        println("--round  = $minorRound")
        println("--gas    = $gasUsed")
        println("--gasCost= $gasCost")
        println("--extra  = ${getExtraData()}")
    }
}

fun getHeaderKey(height: Long, hash: ByteArray): ByteArray {
    return ByteUtil.merge(ByteArray(1) {'h'.toByte()}, height.bytes(), hash)
}