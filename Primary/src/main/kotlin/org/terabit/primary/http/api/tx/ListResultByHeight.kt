package org.terabit.primary.http.api.tx

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import org.terabit.core.MsHttpRequest
import org.terabit.core.TxCode
import org.terabit.network.sendJson
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.txStore
import org.terabit.server.HttpApi
import org.terabit.server.User
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.primary.http.api.block.getBlockByHeight

@MsWebApi(name = "list_result_by_height")
class ListResultByHeight: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val block = getBlockByHeight(ctx, request.getLongParam("height")) ?: return
        if (block.txList.size == 0) {
            return sendApiCode(ctx, ApiCode.EMPTY)
        }
        val result = JsonObject()
        val codes = JsonArray()
        val gas = JsonArray()
        for (tx in block.txList) {
            val receipt = txStore.findTxReceipt(tx.getHash())
            codes.add((receipt?.txState ?: TxCode.FAILED).code)
            gas.add(receipt?.gasUsed ?: 0)
        }

        result.addProperty("code", ApiCode.SUCCESS.code)
        result.add("result", codes)
        result.add("gas", gas)
        sendJson(ctx, result.toString())
    }
}