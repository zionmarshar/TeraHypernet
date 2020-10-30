package org.terabit.core.sync

import org.terabit.common.bytes
import org.terabit.common.toLong
import org.terabit.core.BlockHeader
import org.terabit.core.base.TerabitData
import org.terabit.primary.ThatPrimaryNode
import org.ethereum.util.RLP
import org.ethereum.util.RLPList

class SyncHeader(val height: Long, val minorRound: Long, private val mixHash: ByteArray): TerabitData {

    constructor(rlpList: RLPList) : this(
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData.toLong(),
            rlpList.next().rlpData
    )

    constructor(header: BlockHeader): this(header.height, header.minorRound, header.getHash())

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(height.bytes()),
                RLP.encodeElement(minorRound.bytes()),
                RLP.encodeElement(mixHash)
        )
    }

    override fun getHash(): ByteArray {
        return mixHash
    }

    override fun verify(vararg data: Any): Boolean {
        return true
    }
}

class SyncHeaderResult(val node: ThatPrimaryNode, val result: Int) {

    companion object {
        const val RT_ERROR  = 1
        const val RT_SHORT  = 2
        const val RT_SAME   = 3
        const val RT_EXTEND = 4
        const val RT_BRANCH = 5
    }

    val headerList = ArrayList<SyncHeader>()

    fun findSyncHeader(height: Long): SyncHeader? {
        for (header in headerList) {
            if (header.height == height) {
                return header
            }
        }
        return null
    }

    private fun getResult(): String {
        return when(result) {
            RT_ERROR -> "ERROR"
            RT_SHORT -> "SHORT"
            RT_SAME -> "SAME"
            RT_EXTEND -> "EXTEND"
            RT_BRANCH -> "BRANCH"
            else -> "ELSE"
        }
    }

    override fun toString(): String {
        return "${getResult()}   $node   height:${if (headerList.size == 0) 0 else headerList[0].height}"
    }
}