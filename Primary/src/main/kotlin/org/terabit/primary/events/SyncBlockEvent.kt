package org.terabit.primary.events

import io.netty.channel.ChannelHandlerContext
import org.terabit.primary.ThatPrimaryNode

class SyncBlockEvent(val height: Long, ctx: ChannelHandlerContext, ip: String, port: Int)
    : ThatPrimaryNode(ip, port) {
    init {
        this.ctx = ctx
    }
}
