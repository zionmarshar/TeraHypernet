package org.terabit.primary.http.api.tx

import io.netty.channel.ChannelHandlerContext
import org.terabit.core.*
import org.terabit.core.Env.txPool
import org.terabit.core.base.decodeTeraData
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendJson
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.http.broadFromApi
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "send", method = "post")
class Send: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val body = request.getPostDataStr()
        println("------------------send: body=$body")
        if (body == null || body.isEmpty()) {
            sendApiCode(ctx, ApiCode.PARAM_ERROR)
            return
        }
        if (Env.bestBlock == null) {
            sendApiCode(ctx, ApiCode.NOT_STARTED)
            return
        }
        try {
            val tx = decodeTeraData(body, Transaction::class.java)
            println("------------------send: tx=$tx")

            val sender = Env.curState?.findOrCreateAccount(tx.getFrom())
            if (sender == null) {
                sendApiCode(ctx, ApiCode.NOT_STARTED)
                return
            }

            if (!tx.verify()) {
                sendApiCode(ctx, ApiCode.DATA_ERROR)
                return
            }

            println("------------------send: sender=$sender")
            val result = txPool.newTx(tx)
            if (result != TxCode.SUCCESS) {
                sendJson(ctx, """{"code":${ApiCode.FETCH_ERROR},"txCode":${result.code},"desc":"${result.desc}"}""")
                return
            }

            broadFromApi(tx)

            sendApiCode(ctx, ApiCode.SUCCESS)
        } catch (e: Exception) {
            e.printStackTrace()
            sendApiCode(ctx, ApiCode.SERVER_ERROR)
        }
    }
}