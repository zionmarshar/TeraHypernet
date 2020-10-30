package org.terabit.primary.impl

import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.terabit.common.base64Dec
import org.terabit.core.base.TerabitData
import org.terabit.core.FinalBlock
import org.terabit.core.base.decodeTeraData
import org.terabit.core.base.decodeList
import org.terabit.core.sync.SyncHeader
import org.terabit.core.sync.SyncHeaderResult
import org.terabit.network.*
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.events.*
import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.greenrobot.eventbus.EventBus
import java.net.InetSocketAddress

@Suppress("DuplicatedCode")
class PrimaryRemoteCmdHandler : SimpleChannelInboundHandler<RemoteCmd>() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val addr = ctx.channel().remoteAddress() as InetSocketAddress
        val event = ActiveEvent(addr.address.hostAddress, addr.port)
        event.ctx = ctx
        EventBus.getDefault().post(event)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        val addr = ctx.channel().remoteAddress() as InetSocketAddress
        val event = InactiveEvent(addr.address.hostAddress, addr.port)
        event.ctx = ctx
        EventBus.getDefault().post(event)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: RemoteCmd?) {
        // these connections are long connections.
        // differentiate them will have simpler handler.
        // but need 1 more port.
        // some of the request is one time. the requester will close the connection after they get
        // the result. for example, Kademlia ping/pong message.

        // wait for final block(2) and start competition
        // wait for transaction: from neighbours or nodes
        // wait for candidate block(0) broadcast, relay to secondary miners for seal.
        // wait for candidate block(1) for joint seal
        // wait for secondary miner seal result
        // wait for joint seal result.

        //can do this in business thread, so dealing with large data is OK

        if (msg == null) {
            return
        }
        val uuid = msg.getStringParam("uuid")
        if (uuid != null) {
            synchronized(kBucket.broadcastMessages) {
                if (kBucket.hasReceived(uuid)) {
                    return
                }
                kBucket.recordBroadMsg(uuid)
            }
        }
        println("--recv cmd: ${msg.cmd}     uuid=$uuid")

        val eventBus = EventBus.getDefault()
        val addr = ctx.channel().remoteAddress() as InetSocketAddress
        when(msg.cmd){
            CMD_PING_STR->{
                val port = msg.getIntParam("port") ?: return
                val kid = msg.getStringParam("kid") ?: return
                val isPrimary = msg.getBooleanParam("primary") ?: return
                val event = PingEvent(isPrimary, addr.address.hostAddress, port)
                event.kid = ByteUtil.hexStringToBytes(kid)
                event.ctx = ctx
                eventBus.post(event)
            }
            CMD_PONG_STR->{
                eventBus.post(PongEvent())
            }
            CMD_FIND_NODE_STR->{
                val port = msg.getIntParam("port") ?: return
                val event = FindNodeEvent(addr.address.hostAddress, port)
                val kid = msg.getStringParam("kid")
                if (kid != null && kid.isNotEmpty()) { //request
                    event.kid = ByteUtil.hexStringToBytes(kid)
                } else { //response
                    val list = msg.getJsonArray("list")
                    if (list != null && list.size() > 0) {
                        for (a in list) {
                            if (a !is JsonObject) {
                                continue
                            }
                            val ip = a.get("ip")?.asString ?: continue
                            val p = a.get("port")?.asInt ?: continue
                            event.nodeList.add(ThatPrimaryNode(ip, p))
                        }
                    }
                }
                event.ctx = ctx
                eventBus.post(event)
            }
            CMD_STOP_SEAL_STR -> {
                eventBus.post(StopSealEvent())
            }
            CMD_TERABIT_DATA -> {
                val clsName = msg.getStringParam("cls") ?: return
                val data = msg.getStringParam("data") ?: return
                try {
                    val cls = Class.forName(clsName) as? Class<TerabitData> ?: return
                    val terabit = decodeTeraData(base64Dec(data), cls)
                    eventBus.post(terabit)
                    if (uuid != null) {
                        kBucket.sendBroadcast(terabit, uuid)
                    }
                } catch (e: Exception) {
                }
            }

            CMD_SYNC_HEADER -> {
                val data = msg.getStringParam("data") ?: return
                val port = msg.getIntParam("port") ?: return
                try {
                    val header = SyncHeader(RLP.decodeList(base64Dec(data)))
                    eventBus.post(SyncHeaderEvent(header, ctx, addr.address.hostAddress, port))
                } catch (e: Exception) {
                }
            }
            CMD_SYNC_HEADER_RESULT -> {
                val result = msg.getIntParam("result") ?: return
                val port = msg.getIntParam("port") ?: return
                val data = msg.getStringParam("data")
                try {
                    val node = ThatPrimaryNode(addr.address.hostAddress, port)
                    node.ctx = ctx
                    val obj = SyncHeaderResult(node, result)
                    if (data != null) {
                        obj.headerList.addAll(decodeList(base64Dec(data), SyncHeader::class.java))
                    }
                    eventBus.post(obj)
                } catch (e: Exception) {
                }
            }

            CMD_LIST_HEADER -> {
                val height = msg.getLongParam("height") ?: return
                val count = msg.getIntParam("count") ?: return
                val port = msg.getIntParam("port") ?: return
                eventBus.post(ListHeaderEvent(height, count, ctx, addr.address.hostAddress, port))
            }
            CMD_LIST_HEADER_RESULT -> {
                val height = msg.getLongParam("height") ?: return
                val data = msg.getStringParam("data")
                try {
                    val list = if (data == null) {
                        null
                    } else {
                        decodeList(base64Dec(data), SyncHeader::class.java)
                    }
                    eventBus.post(ListHeaderResultEvent(list, height))
                } catch (e: Exception) {
                }
            }

            CMD_SYNC_BLOCK -> {
                val height = msg.getLongParam("height") ?: return
                val port = msg.getIntParam("port") ?: return
                eventBus.post(SyncBlockEvent(height, ctx, addr.address.hostAddress, port))
            }
            CMD_SYNC_BLOCK_RESULT -> {
                val result = msg.getIntParam("result") ?: return
                val data = msg.getStringParam("data")
                try {
                    val block = if (data == null) {
                        null
                    } else {
                        FinalBlock(RLP.decodeList(base64Dec(data)))
                    }
                    eventBus.post(SyncBlockResultEvent(result, block))
                } catch (e: Exception) {
                }
            }

            "REQUEST_SEAL"->{
                //forward the hashcode only. the secondary miner will sign this hash code, if it win, it will
                //then download the full block and validate that block and then send back it to primary miner.

                eventBus.post(RequestSealEvent())
            }
            "REQUEST_JOINT_SEAL"->{
                //also send the channel, data should be read later. in another handler?

                eventBus.post(RequestJointSealEvent())
            }
        }
    }
}
