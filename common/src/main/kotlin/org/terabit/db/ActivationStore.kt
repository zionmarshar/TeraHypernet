package org.terabit.db

import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.common.CONTRACT_ACTIVATION_VALID_PERIOD
import org.terabit.common.bytes
import org.terabit.common.toLong
import org.terabit.core.FinalBlock
import org.terabit.core.base.TerabitData
import org.terabit.core.base.decodeList
import org.terabit.core.base.encodeList
import java.util.*

//only for activation
class ActivationStore(dbName: String) {
    private val db = LevelDbDataSource(dbName)

    fun saveActivation(act: ActivationInfo) {
        db.put(act.address, act.getData())
    }

    fun findActivation(addr: ByteArray): ActivationInfo? {
        val bytes = db.get(addr) ?: return null
        return ActivationInfo(addr, bytes)
    }

    //checkTime: unit: s
    fun hasActivation(addr: ByteArray, checkTime: Long): Boolean {
        val actInfo = findActivation(addr)
        if (actInfo == null || actInfo.activations.size == 0) { //permission denied
            println("----address:[${ByteUtil.toHexString(addr)}], no activation information")
            return false
        }
        val act = actInfo.activations.lastOrNull()
        if (act == null || !act.isValid(checkTime)) { //expired
            println("----address:[${ByteUtil.toHexString(addr)}], activation expired")
            return false
        }
        return true
    }
}

class ActivationInfo {
    var address: ByteArray = EMPTY_BYTE_ARRAY
    val activations = LinkedList<Activation>()

    constructor(address: ByteArray, data: ByteArray) {
        this.address = address
        val list = decodeList(data, Activation::class.java)
        this.activations.addAll(list)
    }

    constructor(block: FinalBlock, txIndex: Int, flag: Int = 0) {
        this.address = block.txList[txIndex].getFrom()
        val act = Activation(block.header.timestamp, block.header.timestamp + CONTRACT_ACTIVATION_VALID_PERIOD,
                block.getHeight(), txIndex, flag)
        activations.add(act)
    }

    constructor(address: ByteArray, blockHeight: Long, txIndex: Int, timeStart: Long, timeEnd: Long, flag: Int) {
        this.address = address
        val act = Activation(timeStart, timeEnd, blockHeight, txIndex, flag)
        activations.add(act)
    }

    constructor(address: ByteArray, blockHeight: Long, txIndex: Int, timeStart: Long, flag: Int = 0): this(
            address, blockHeight, txIndex, timeStart, timeStart + CONTRACT_ACTIVATION_VALID_PERIOD, flag
    )

    fun getData(): ByteArray {
        return encodeList(activations)
    }

    override fun toString(): String {
        return "[ActivationInfo:: address:${ByteUtil.firstToHexString(address)} acts:$activations]"
    }
}

class Activation(val timeStart: Long, val timeEnd: Long, val blockHeight: Long,
                 val txIndex: Int, val flag: Int = 0): TerabitData {

    constructor(rlp: RLPList): this(
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData.toLong(),
            ByteUtil.byteArrayToInt(rlp.next().rlpData),
            ByteUtil.byteArrayToInt(rlp.next().rlpData)
    )

    override fun getHash(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun encode(): ByteArray {
        return RLP.encodeList(
                RLP.encodeElement(timeStart.bytes()),
                RLP.encodeElement(timeEnd.bytes()),
                RLP.encodeElement(blockHeight.bytes()),
                RLP.encodeElement(ByteUtil.intToBytes(txIndex)),
                RLP.encodeElement(ByteUtil.intToBytes(flag))
        )
    }

    override fun verify(vararg data: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "[Activation:: height:$blockHeight index:$txIndex start:$timeStart end:$timeEnd]"
    }

    //checkTime: second
    fun isValid(checkTime: Long): Boolean {
        return timeEnd == 0L || timeEnd > checkTime
    }
}