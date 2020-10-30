package org.terabit.primary.http.api.block

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.Env
import org.terabit.core.MsHttpRequest
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendJson
import org.terabit.primary.http.MsWebApi
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "monitor")
class Monitor: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val reqHeight = request.getIntParam("height") ?: 0
        var best = Env.bestBlock?.getHeight() ?: 0

        if (best <= reqHeight) {
            synchronized(Env.monitorLock) {
                if (Env.monitorCount >= Env.MONITOR_COUNT_LIMITED) {
                    return sendApiCode(ctx, ApiCode.NUMBER_LIMITED)
                }
                Env.monitorCount++
                Env.monitorLock.wait(180000)
            }

            best = Env.bestBlock?.getHeight() ?: 0
        }

        sendJson(ctx, """{"code":${ApiCode.SUCCESS},"height":$best}""")
    }
}