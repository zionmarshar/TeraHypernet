package org.terabit.primary.http.api.account

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.MsHttpRequest
import org.terabit.primary.http.MsWebApi
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "get_by_height")
class GetByHeight: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        getByBlock(ctx, request, false)
    }
}