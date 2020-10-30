package org.terabit.primary.impl

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import org.terabit.common.curTime
import org.terabit.network.RemoteCmd
import org.terabit.network.CmdCodec


class PrimaryChannelInitializer :  ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch1: SocketChannel?) {
        val pipe = ch1?.pipeline()
            ?: return

        // these connections are long connections.
        // differentiate them will have simpler handler.
        // but need 1 more port.
        // some of the request is one time. the requester will close the connection after they get
        // the result. for example, Kademlia ping/pong message.

        // wait for final block(2) and start competition
        // wait for transaction: from neighbours or nodes
        // wait for candidate block(0) broadcast, relay to secondary miners for seal.
        // wait for candidate block(1) for joint seal
        // wait for secondary miner seal result
        // wait for joint seal result.
        val writeTimer = object: ChannelOutboundHandlerAdapter(){
            override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
                if(msg !is RemoteCmd)
                    ctx?.channel()?.lastSendTime = curTime()

                super.write(ctx, msg, promise)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                cause?.printStackTrace()
            }
        }
        with(pipe) {
            addLast("cmdCodec", CmdCodec())
            addLast("httpCodec", HttpServerCodec())
            addLast("remoteCmdHandler", PrimaryRemoteCmdHandler())
            addLast("httpHandler", PrimaryHttpHandler())
            addLast("writeTimeRecorder", writeTimer)
        }
    }
}
