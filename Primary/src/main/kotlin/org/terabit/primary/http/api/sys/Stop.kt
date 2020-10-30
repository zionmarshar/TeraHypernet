package org.terabit.primary.http.api.sys

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.MsHttpRequest
import org.terabit.primary.http.MsWebApi
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "stop")
class Stop: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
    }
}