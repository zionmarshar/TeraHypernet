package org.terabit.core

import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.common.bytes
import org.terabit.common.getSha256
import org.terabit.common.toLong
import org.terabit.core.base.TerabitData

class TransactionReceipt(val blockHeight: Long,
                         val index: Int,
                         val txHash: ByteArray,
                         val txState: TxCode,
                         val gasUsed: Long
): TerabitData {
    private var mixHash: ByteArray = ByteUtil.EMPTY_BYTE_ARRAY

    private fun hash() {
        if (mixHash.isNotEmpty()) {
            return
        }
        mixHash = getSha256(blockHeight.bytes(), ByteUtil.intToBytes(index),
                ByteUtil.intToBytes(txState.code), gasUsed.bytes())
    }

    constructor(rlp: RLPList): this(
            rlp.next().rlpData.toLong(),
            RLP.decodeInt(rlp.next().rlpData, 0),
            rlp.next().rlpData,
            TxCode.create(RLP.decodeInt(rlp.next().rlpData, 0)),
            rlp.next().rlpData.toLong()
    )

    override fun getHash(): ByteArray {
        hash()
        return mixHash
    }

    override fun verify(vararg data: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(blockHeight.bytes()),
                RLP.encodeInt(index),
                RLP.encodeElement(txHash),
                RLP.encodeInt(txState.code),
                RLP.encodeElement(gasUsed.bytes())
        )
    }

    override fun toString(): String {
        return "[Receipt:: blockHeight:$blockHeight," +
                " index:$index, txState:$txState, gasUsed:$gasUsed]"
    }
}
