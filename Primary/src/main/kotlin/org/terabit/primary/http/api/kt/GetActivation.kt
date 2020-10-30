package org.terabit.primary.http.api.kt

import io.netty.channel.ChannelHandlerContext
import org.ethereum.util.ByteUtil
import org.terabit.core.MsHttpRequest
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendTerabitData
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.actStore
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "get_activation")
class GetActivation: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val addr = request.getStringParam("addr") ?: return sendApiCode(ctx, ApiCode.PARAM_ERROR)

        try {
            val info = actStore.findActivation(ByteUtil.hexStringToBytes(addr)) ?: return sendApiCode(ctx, ApiCode.NOT_FOUND)
            if (info.activations.isEmpty()) {
                return sendApiCode(ctx, ApiCode.EMPTY)
            }

            sendTerabitData(ctx, info.activations.last)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sendApiCode(ctx, ApiCode.SERVER_ERROR)
    }
}