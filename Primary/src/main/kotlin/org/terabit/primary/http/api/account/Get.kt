package org.terabit.primary.http.api.account

import io.netty.channel.ChannelHandlerContext
import org.ethereum.util.ByteUtil
import org.terabit.core.MsHttpRequest
import org.terabit.core.state.StateDb
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendJson
import org.terabit.network.sendTerabitData
import org.terabit.primary.http.MsDontCare
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.blockStore
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "get")
class Get: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        getByBlock(ctx, request, true)
    }
}

@MsDontCare
fun getByBlock(ctx: ChannelHandlerContext, request: MsHttpRequest, isBest: Boolean) {
    val addr = request.getStringParam("addr") ?: return sendApiCode(ctx, ApiCode.PARAM_ERROR)

    try {
        val block = if (isBest) {
            blockStore.getBestBlock()
        } else {
            val height = request.getLongParam("height") ?: return sendApiCode(ctx, ApiCode.PARAM_ERROR)
            blockStore.getChainBlockByHeight(height)
        } ?: return sendApiCode(ctx, ApiCode.PARAM_ERROR)

        val trie = StateDb.load(block.header.stateRoot)
        val obj = trie.getStateObject(ByteUtil.hexStringToBytes(addr)) ?: return sendApiCode(ctx, ApiCode.NOT_FOUND)
        sendTerabitData(ctx, obj)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    sendApiCode(ctx, ApiCode.SERVER_ERROR)
}