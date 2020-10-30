package org.terabit.primary.events

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.sync.SyncHeader
import org.terabit.primary.ThatPrimaryNode

class SyncHeaderEvent(val header: SyncHeader, ctx: ChannelHandlerContext, ip: String, port: Int)
    : ThatPrimaryNode(ip, port) {
    init {
        this.ctx = ctx
    }
}
