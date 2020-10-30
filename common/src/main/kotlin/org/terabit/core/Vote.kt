package org.terabit.core

import org.terabit.common.asymDecrypt
import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.common.bytes
import org.terabit.common.toLong
import org.terabit.core.base.PublicKeyData
import org.terabit.core.base.TerabitData

class Vote(val signedBytes: ByteArray,
           pubKey: ByteArray,
           val blockHeight: Long): PublicKeyData(pubKey), TerabitData {

    private var mixHash: ByteArray = ByteUtil.EMPTY_BYTE_ARRAY

    constructor(rlp: RLPList): this(rlp.next().rlpData, rlp.next().rlpData, rlp.next().rlpData.toLong())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vote

        if (!signedBytes.contentEquals(other.signedBytes)) return false
        if (!pubKey.contentEquals(other.pubKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signedBytes.contentHashCode()
        result = 31 * result + pubKey.contentHashCode()
        return result
    }

    override fun getHash(): ByteArray {
        if (mixHash.isEmpty()) {
            mixHash = asymDecrypt(pubKey, signedBytes)
        }
        return mixHash
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(signedBytes),
                RLP.encodeElement(pubKey),
                RLP.encodeElement(blockHeight.bytes())
        )
    }

    override fun verify(vararg data: Any): Boolean {
        val hash = (if (data.isNotEmpty()) (data[0] as? ByteArray ) else null) ?: return false
        getHash()
        return mixHash.contentEquals(hash)
    }

    override fun toString(): String {
        return "[Vote:: sign:${ByteUtil.firstToHexString(signedBytes)} height:${blockHeight}]"
    }
}