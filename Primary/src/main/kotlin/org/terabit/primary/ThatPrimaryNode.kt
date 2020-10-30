package org.terabit.primary

import com.google.gson.Gson
import io.netty.channel.ChannelHandlerContext
import org.terabit.core.base.TerabitData
import org.terabit.network.RemoteCmd
import org.terabit.primary.impl.createTerabitDataCmd
import org.ethereum.util.ByteUtil

//it is a Netty client. will be initialized when setup kbucket or ping by other node.
open class ThatPrimaryNode(val ip: String, val port: Int) {

    var kid: ByteArray = ByteUtil.EMPTY_BYTE_ARRAY //use account's address(now the public key's hash)
    var ctx: ChannelHandlerContext? = null

    fun send(data: TerabitData) {
        //do we need a long connection to remote primary node?
        //do by netty write handler. it will receive any terabit object
        if (ctx == null) {
            return
        }
        val cmd = createTerabitDataCmd(data)
        ctx?.writeAndFlush(cmd)
    }

    fun sendCmd(cmd: RemoteCmd) {
        ctx?.writeAndFlush(cmd)
    }

    override fun equals(other: Any?): Boolean {
        if(other !is ThatPrimaryNode)
            return false
        return this.kid.contentEquals(other.kid)
    }

    override fun toString(): String {
        return "{$ip:$port,${ByteUtil.toHexString(kid)}}"
    }

    fun getKey(): String {
        return getKey(ip, port)
    }

    fun isSameKey(node: ThatPrimaryNode): Boolean {
        return isSameKey(node.ip, node.port)
    }

    fun isSameKey(ip: String, port: Int): Boolean {
        return (ip == "0.0.0.0" || this.ip == ip) && this.port == port
    }
}

fun getKey(ip: String, port: Int): String {
    return "{$ip:$port}"
}

fun doForAllBookKeeper(bookKeepers:ArrayList<ThatPrimaryNode>, task:(bookKeeper:ThatPrimaryNode)->Unit){
    bookKeepers.forEach { task(it) }
}

fun doForAllThosePrimaryNode(thosePrimaryNode: ArrayList<ThatPrimaryNode>, task:(thatNode:ThatPrimaryNode)->Unit){
    thosePrimaryNode.forEach { task(it) }
}

private fun objToCmdImpl(obj:Any): RemoteCmd {
    val cls = obj::class.simpleName
    return RemoteCmd(cls+ Gson().toJson(obj))
}

fun sendObject(ctx: ChannelHandlerContext?, obj:Any){
    if (ctx == null) {
        return
    }
    val cmd = objToCmdImpl(obj)
    ctx.writeAndFlush(cmd)
}