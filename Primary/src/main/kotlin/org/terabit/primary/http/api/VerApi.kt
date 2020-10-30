package org.terabit.primary.http.api


import io.netty.channel.ChannelHandlerContext
import org.terabit.core.MsHttpRequest
import org.terabit.network.sendJson
import org.terabit.primary.http.MsWebApi
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi
class VerApi: HttpApi(){
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        sendJson(ctx,"""{"ver":1.0.00}""")
    }
}
