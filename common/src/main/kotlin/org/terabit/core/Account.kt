package org.terabit.core

import org.terabit.common.base64Dec
import org.terabit.common.getSha256
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY

class Account {
    var uuid: String = ""
    var pswHash: String = ""  //real password for test
    var pubKey: String = ""
    var priKey: String = ""

    var priKeyBytes = EMPTY_BYTE_ARRAY
    var pubKeyBytes = EMPTY_BYTE_ARRAY
    private var addressBytes = EMPTY_BYTE_ARRAY

    fun getAddrBytes(): ByteArray {
        if (addressBytes.isNotEmpty()) {
            return addressBytes
        }
        if (pubKeyBytes.isEmpty()) {
            pubKeyBytes = base64Dec(pubKey)
        }
        addressBytes = getSha256(pubKeyBytes)
        return addressBytes
    }

    fun getAddr(): String {
        getAddrBytes()
        return ByteUtil.toHexString(addressBytes)
    }

    fun isWrong(): Boolean {
        return uuid.isEmpty() || pswHash.isEmpty() || pubKey.isEmpty() || priKey.isEmpty()
    }

    override fun toString(): String {
        return "$uuid,$pswHash\n$priKey\n$priKey"
    }
}
