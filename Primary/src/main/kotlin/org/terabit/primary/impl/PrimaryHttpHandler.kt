package org.terabit.primary.impl

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpRequest

class PrimaryHttpHandler : SimpleChannelInboundHandler<HttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HttpRequest?) {
        //dispatch in api way.
    }

}
