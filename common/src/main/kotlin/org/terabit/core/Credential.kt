package org.terabit.core

import org.terabit.common.*
import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.core.base.PublicKeyData
import org.terabit.core.base.TerabitData

var credentialDifficulty = DEFAULT_DIFFICULTY

class Credential(val signedBytes:ByteArray,
                 pubKey: ByteArray,
                 val round: Long): PublicKeyData(pubKey), TerabitData {

    var blockHeight = 0L
    var isMinorSeal = false

    constructor(rlp: RLPList): this(
            rlp.next().rlpData,
            rlp.next().rlpData,
            rlp.next().rlpData.toLong()
    ) {
        blockHeight = rlp.next().rlpData.toLong()
        isMinorSeal = rlp.next().rlpData[0] == 1.toByte()
    }

    override fun getHash(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(signedBytes),
                RLP.encodeElement(pubKey),
                RLP.encodeElement(round.bytes()),
                RLP.encodeElement(blockHeight.bytes()),
                RLP.encodeByte(if (isMinorSeal) 1 else 2)
        )
    }

    override fun verify(vararg data: Any): Boolean {
        val blockHash = (if (data.isNotEmpty()) (data[0] as? ByteArray) else null) ?: return false
        return verifyRoundHash(signedBytes, pubKey, round, credentialDifficulty, blockHash)
    }

    override fun toString(): String {
        return "[Credential:: sign:${ByteUtil.firstToHexString(signedBytes)}, " +
                "addr:${ByteUtil.firstToHexString(getFrom())} height:${blockHeight}," +
                " round:$round, isMinor:$isMinorSeal]"
    }
}