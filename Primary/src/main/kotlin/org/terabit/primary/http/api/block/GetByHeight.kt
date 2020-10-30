package org.terabit.primary.http.api.block

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.FinalBlock
import org.terabit.core.MsHttpRequest
import org.terabit.network.*
import org.terabit.primary.http.MsDontCare
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.blockStore
import org.terabit.server.HttpApi
import org.terabit.server.User


@MsWebApi(name = "get_by_height")
class GetByHeight: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val block = getBlockByHeight(ctx, request.getLongParam("height")) ?: return
        sendTerabitData(ctx, block)
    }
}

@MsDontCare
fun getBlockByHeight(ctx: ChannelHandlerContext, height: Long?): FinalBlock? {
    if (height == null || height < 0) {
        sendApiCode(ctx, ApiCode.PARAM_ERROR)
        return null
    }
    val block = blockStore.getChainBlockByHeight(height)
    if (block == null) {
        sendApiCode(ctx, ApiCode.NOT_FOUND)
        return null
    }
    return block
}