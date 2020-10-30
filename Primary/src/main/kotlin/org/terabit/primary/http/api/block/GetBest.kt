package org.terabit.primary.http.api.block

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.FinalBlock
import org.terabit.core.MsHttpRequest
import org.terabit.network.*
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.blockStore
import org.terabit.server.HttpApi
import org.terabit.server.User


@MsWebApi(name = "get_best")
class GetBest: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val block = blockStore.getBestBlock() ?: return sendApiCode(ctx, ApiCode.NOT_INIT)
        sendTerabitData(ctx, block)
    }
}