package org.terabit.primary.http.api.account

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import org.ethereum.util.ByteUtil
import org.terabit.core.MsHttpRequest
import org.terabit.core.state.StateDb
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendJson
import org.terabit.primary.http.MsDontCare
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.http.api.block.getBlockByHeight
import org.terabit.server.HttpApi
import org.terabit.server.User


@MsWebApi(name = "list_some_by_height", method = "post")
class ListSomeByHeight: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val json = getPostJson(ctx, request) ?: return

        try {
            val addrArray = json["addr"].asJsonArray

            val block = getBlockByHeight(ctx, json["height"]?.asLong)
                    ?: return
            val trie = StateDb.load(block.header.stateRoot)
            val sbBalance = StringBuilder()
            val sbNonce = StringBuilder()
            for (addr in addrArray) {
                if (sbBalance.isNotEmpty()) {
                    sbBalance.append(',')
                    sbNonce.append(',')
                }
                val obj = trie.getStateObject(ByteUtil.hexStringToBytes(addr.asString))
                val b = obj?.balance ?: 0
                val n = obj?.nonce ?: 0
                sbBalance.append(b)
                sbNonce.append(n)
            }
            sendJson(ctx, """{"code":0,"balance":[${sbBalance}],"nonce":[${sbNonce}]}""")
        } catch (e: Exception) {
        }

        sendApiCode(ctx, ApiCode.SERVER_ERROR)
    }
}

@MsDontCare
fun getPostJson(ctx: ChannelHandlerContext, request: MsHttpRequest): JsonObject? {
    val body = request.getPostDataStr()
    if (body == null || body.isEmpty()) {
        sendApiCode(ctx, ApiCode.PARAM_ERROR)
        return null
    }
    return try {
        JsonParser().parse(body) as JsonObject
    } catch (e: Exception) {
        sendApiCode(ctx, ApiCode.NOT_JSON)
        null
    }
}