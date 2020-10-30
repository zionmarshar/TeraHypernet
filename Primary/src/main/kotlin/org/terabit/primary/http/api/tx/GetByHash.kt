package org.terabit.primary.http.api.tx

import io.netty.channel.ChannelHandlerContext
import org.ethereum.util.ByteUtil
import org.terabit.core.MsHttpRequest
import org.terabit.network.ApiCode
import org.terabit.network.sendApiCode
import org.terabit.network.sendTerabitData
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.impl.blockStore
import org.terabit.primary.impl.txStore
import org.terabit.server.HttpApi
import org.terabit.server.User

@MsWebApi(name = "get_by_hash")
class GetByHash: HttpApi() {
    override fun onRequest(ctx: ChannelHandlerContext, request: MsHttpRequest, user: User?) {
        val txHash = request.getStringParam("hash") ?: return sendApiCode(ctx, ApiCode.PARAM_ERROR)
        val receipt = txStore.findTxReceipt(ByteUtil.hexStringToBytes(txHash))
                ?: return sendApiCode(ctx, ApiCode.NOT_FOUND)

        val blockHash = blockStore.getHashByHeight(receipt.blockHeight)
                ?: return sendApiCode(ctx, ApiCode.NOT_FOUND)

        val tx = blockStore.getTx(blockHash, receipt.index) ?: return sendApiCode(ctx, ApiCode.NOT_FOUND)
        sendTerabitData(ctx, tx)
    }
}