package org.terabit.core

import org.terabit.common.*
import org.ethereum.util.ByteUtil.*
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.core.base.PublicKeyData
import org.terabit.core.base.TerabitData

class Transaction(val nonce: Long,
                  val amount: Long,
                  val gasPrice: Long,
                  val gasLimit: Long,
                  pubKey: ByteArray,
                  val to: ByteArray,
                  val data: ByteArray = EMPTY_BYTE_ARRAY
): PublicKeyData(pubKey), TerabitData {
    private var mixHash: ByteArray = EMPTY_BYTE_ARRAY
    private var sign: ByteArray = EMPTY_BYTE_ARRAY
    var result = TxCode.SUCCESS

    var gasUsed = 0

    constructor(rlp: RLPList): this(
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData.toLong(),
            rlp.next().rlpData,
            rlp.next().rlpData,
            rlp.next().rlpData
    ) {
        sign = rlp.next().rlpData
    }

    fun hash() {
        getFrom()
        if (mixHash.isNotEmpty()) {
            return
        }
        mixHash = getSha256(nonce.bytes(), amount.bytes(), gasPrice.bytes(),
                gasLimit.bytes(), getFrom(), to, data)
    }

    fun sign(priKey: ByteArray) {
        hash()
        sign = sign(priKey, mixHash)
    }

    override fun verify(vararg data: Any): Boolean {
        //only verify data
        hash()
        if (getFrom().isEmpty() || getFrom().size != ADDRESS_SIZE || amount < 0 || nonce < 0
                || gasPrice < 0 || gasLimit < 0 || this.data.size > TX_DATA_SIZE_MAX) {
            return false
        }
        if (to.isNotEmpty() && to.size != ADDRESS_SIZE) {
            return false
        }

        val h = asymDecrypt(pubKey, sign)
        return mixHash.contentEquals(h)
    }

    override fun getHash(): ByteArray {
        hash()
        return mixHash
    }

    override fun encode(): ByteArray {
        hash()
        return RLP.encodeList(
                RLP.encodeElement(nonce.bytes()),
                RLP.encodeElement(amount.bytes()),
                RLP.encodeElement(gasPrice.bytes()),
                RLP.encodeElement(gasLimit.bytes()),
                RLP.encodeElement(pubKey),
                RLP.encodeElement(to),
                RLP.encodeElement(data),
                RLP.encodeElement(sign)
        )
    }

    private fun hasKtData(): Boolean {
        //contract format: version(2B)+method(2B)+data
        return to.isEmpty() && data.isNotEmpty() && data.size >= 4
    }

    fun ktVersion(): Short {
        return if (hasKtData()) bigEndianToShort(data) else -1
    }

    fun ktMethod(): Short {
        return if (hasKtData()) bigEndianToShort(data, 2) else -1
    }

    override fun toString(): String {
        return "[Transaction:: from:${firstToHexString(getFrom())}, to:${firstToHexString(to)}," +
                " amount:$amount, nonce:$nonce, price:$gasPrice, limit:$gasLimit]"
    }
}

fun createTransaction(nonce: Long, amount: Long, gasPrice: Long, gasLimit: Long, priKey: ByteArray,
                      pubKey: ByteArray, to: ByteArray, data: ByteArray = EMPTY_BYTE_ARRAY): Transaction {
    val tran = Transaction(nonce, amount, gasPrice, gasLimit, pubKey, to, data)
    tran.sign(priKey)
    return tran
}

enum class TxCode(val code: Int, val desc: String) {
    SUCCESS(1, "success"),
    FAILED(2, "failed"),
    LARGE_GAS_LIMIT(3, "bigger than block gas limit"),
    LOWER_GAS_PRICE(4, "lower than miner's gas price setting"),
    INSUFFICIENT_BALANCE(5, "insufficient balance"),
    INSUFFICIENT_GAS(6, "insufficient gas"),
    NONCE_ERROR(7, "nonce error"),
    COVER_ERROR(8, "can not cover the same transaction"),
    LIST_FULL(9, "miner's transaction list is full"),
    INVALID_METHOD(125, "invalid method"),
    DATA_ERROR(126, "contract data error"),
    NONE(127, "none");

    companion object {
        fun create(code: Int): TxCode {
            for (c in values()) {
                if (c.code == code) {
                    return c
                }
            }
            return NONE
        }
    }

    override fun toString(): String {
        return "[TxCode:: code=$code name:$name]"
    }
}
