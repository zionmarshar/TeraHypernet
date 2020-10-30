package org.terabit.db

import org.terabit.common.bytes
import org.terabit.common.getSha256
import org.terabit.common.toLong
import org.terabit.core.base.TerabitData
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.ethereum.util.RLP
import org.ethereum.util.RLPList

class StateObject(val address: ByteArray, var balance: Long, var nonce: Long,
                  val storageRoot: ByteArray, val codeHash: ByteArray): TerabitData {
    private var mixHash = EMPTY_BYTE_ARRAY

    var storageData: ByteArray = EMPTY_BYTE_ARRAY

    constructor(address: ByteArray, balance: Long, nonce: Long)
            : this(address, balance, nonce, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY)

    constructor(rlpList: RLPList):this(
            rlpList.next().rlpData,
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData,
            rlpList.next().rlpData
    )

    constructor(encoded: ByteArray):this(RLP.decodeList(encoded))

    override fun getHash(): ByteArray {
        if (mixHash.isEmpty()) {
            mixHash = getSha256(address, nonce.bytes(), balance.bytes())
        }
        return mixHash
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(address),
                RLP.encodeElement(balance.bytes()),
                RLP.encodeElement(nonce.bytes()),
                RLP.encodeElement(storageRoot),
                RLP.encodeElement(codeHash)
        )
    }

    override fun verify(vararg data: Any): Boolean {
        TODO("Not yet implemented")
    }

    fun withAddAmount(amount: Long): StateObject {
        return StateObject(address, balance + amount, nonce, storageRoot, codeHash)
    }

    override fun toString(): String {
        return "[StateObject:: address:${ByteUtil.firstToHexString(address)},balance:$balance,nonce:$nonce]"
    }
}