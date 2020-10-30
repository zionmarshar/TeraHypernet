package org.terabit.core

import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY

// a stub of other primary node.
class OtherPrimary(val ip: String, val port: Int) {
    var kid = EMPTY_BYTE_ARRAY //use account's address(now the public key's hash)

    fun findNearest(kid: String) {

    }

    fun findNode(kid: String) {

    }

    override fun equals(other: Any?): Boolean {
        if(other !is OtherPrimary)
            return false
        return this.kid.contentEquals(other.kid)
    }

    fun ping(): Boolean {
        //ping the remote node, to see if it can return a message.
        return true
    }

    fun connect():Boolean{
        return true
    }
}