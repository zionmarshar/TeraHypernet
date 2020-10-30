package org.terabit.core.base

import org.ethereum.util.ByteUtil
import org.terabit.common.getSha256

open class PublicKeyData(val pubKey: ByteArray) {

    private var from: ByteArray = ByteUtil.EMPTY_BYTE_ARRAY

    fun getFrom(): ByteArray {
        if (pubKey.isEmpty()) {
            return from
        }
        if (from.isEmpty()) {
            from = getSha256(pubKey)
        }
        return from
    }
}