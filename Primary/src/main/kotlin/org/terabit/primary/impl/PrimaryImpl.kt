package org.terabit.primary.impl

//All primary miner implementation come to here.

import com.google.gson.GsonBuilder
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.terabit.common.base64Enc
import org.terabit.core.state.StateDb
import org.terabit.core.sync.SyncHeader
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_ERROR
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_SAME
import org.terabit.core.sync.SyncHeaderResult.Companion.RT_SHORT
import org.terabit.db.BlockStore
import org.terabit.db.TransactionStore
import org.terabit.network.*
import org.terabit.primary.NodeId
import org.terabit.primary.PrimaryConfig
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.gson.ThatPrimaryNodeAdapter
import org.ethereum.util.ByteUtil
import org.greenrobot.eventbus.EventBus
import org.terabit.core.MinorWorldManager
import org.terabit.core.*
import org.terabit.core.base.TerabitData
import org.terabit.core.base.encodeList
import org.terabit.db.ActivationStore

val blockStore = BlockStore(if (NodeId != 10000) "Test$NodeId/block" else "block")
val txStore = TransactionStore(if (NodeId != 10000) "Test$NodeId/txInfo" else "txInfo")
val actStore = ActivationStore(if (NodeId != 10000) "Test$NodeId/activation" else "activation")
val kBucket = KBucket()
private val worldManager = WorldManager(blockStore, txStore, kBucket)
private val minorWorldManager = MinorWorldManager()

fun initEventBus() {
    EventBus.getDefault().register(kBucket)
    EventBus.getDefault().register(worldManager)
}

//it should be a blocking operation. no neighbour nodes, can not start the rest works.
fun initKBucket(): Boolean {
    StateDb.init(if (NodeId != 10000) "Test$NodeId/state" else "state")
    kBucket.autoInit()
    worldManager.start()

    return true
}

fun initMinor() {
    EventBus.getDefault().register(minorWorldManager)
    minorWorldManager.start()
}

fun waitForNetworkConnectionAndEvents(port: Int): ChannelFuture? {
    val mBoss = NioEventLoopGroup(1) //for listen
    val mWorker = NioEventLoopGroup(4) //for processing i/o of each socket/channel

    val bs = ServerBootstrap()
    try {
        bs.group(mBoss, mWorker)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(PrimaryChannelInitializer())

        val f = bs.bind(port).sync()
        return if (f.isSuccess) {
            println("************ Dcp Primary miner started at $port ************")
            f
        } else {
            mWorker.shutdownGracefully()
            mBoss.shutdownGracefully()
            println("************ Dcp Primary miner started at $port failed!! ************")
            f
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun connectToThatNode(node: ThatPrimaryNode): Boolean {
    return connectToThatNode(node.ip, node.port)
}

fun connectToThatNode(ip: String, port: Int): Boolean {
    val bs = Bootstrap()
    val mWorker = NioEventLoopGroup(1)
    try {
        bs.group(mWorker)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(PrimaryChannelInitializer()) //use listener handler

        val f = bs.connect(ip, port).sync()
        if (!f.isSuccess) {
            mWorker.shutdownGracefully()
        }
        return f.isSuccess
    } catch (e: Exception) {
        mWorker.shutdownGracefully()
        println("----conn error: ${e.message}")
        return false
    }
}

fun createFindNodeCmd(kid: ByteArray, vararg nodes: ThatPrimaryNode): RemoteCmd {
    if (nodes.isEmpty()) {
        return RemoteCmd("""$CMD_FIND_NODE_STR{"kid":"${ByteUtil.toHexString(kid)}","port":${PrimaryConfig.port}}""")
    }
    //register gson adapter
    val builder = GsonBuilder().registerTypeAdapter(ThatPrimaryNode::class.java, ThatPrimaryNodeAdapter())
    val array = builder.create().toJson(nodes)
    return RemoteCmd("""$CMD_FIND_NODE_STR{"list":$array,"port":${PrimaryConfig.port}}""")
}

fun createPingCmd(): RemoteCmd {
    return RemoteCmd("""$CMD_PING_STR{"kid":"${myAccount.getAddr()}","port":${PrimaryConfig.port},
        |"primary":${PrimaryConfig.isPrimary}}""".trimMargin())
}

fun createTerabitDataCmd(data: TerabitData): RemoteCmd {
    val bytes = data.encode()
    val str = base64Enc(bytes)
    return RemoteCmd("""$CMD_TERABIT_DATA{"cls":"${data.javaClass.name}","data":"$str"}""")
}

fun createSyncHeaderCmd(sync: SyncHeader): RemoteCmd {
    return RemoteCmd("""$CMD_SYNC_HEADER{"data":"${base64Enc(sync.encode())}","port":${PrimaryConfig.port}}""")
}

fun createSyncHeaderResultCmd(result: Int, list: MutableList<SyncHeader>?): RemoteCmd {
    val data = if (result == RT_ERROR || result == RT_SAME || result == RT_SHORT || list == null || list.size == 0)
        ""
    else
        ""","data":"${base64Enc(encodeList(list))}""""

    return RemoteCmd("""$CMD_SYNC_HEADER_RESULT{"result":$result,"port":${PrimaryConfig.port}$data}""")
}

fun createListHeaderCmd(height: Long, count: Int): RemoteCmd {
    return RemoteCmd("""$CMD_LIST_HEADER{"height":$height,"count":$count,"port":${PrimaryConfig.port}}""")
}

fun createListHeaderResultCmd(list: MutableList<SyncHeader>?, height: Long): RemoteCmd {
    val data = if (list == null || list.size == 0)
        ""
    else
        ""","data":"${base64Enc(encodeList(list))}""""

    return RemoteCmd("""$CMD_LIST_HEADER_RESULT{"height":$height$data}""")
}

fun createSyncBlockCmd(height: Long): RemoteCmd {
    return RemoteCmd("""$CMD_SYNC_BLOCK{"height":$height,"port":${PrimaryConfig.port}}""")
}

fun createSyncBlockResultCmd(result: Int, block: FinalBlock?): RemoteCmd {
    val data = if (result == 1 || block == null)
        ""
    else
        ""","data":"${base64Enc(block.encode())}""""

    return RemoteCmd("""$CMD_SYNC_BLOCK_RESULT{"result":$result$data}""")
}

fun isMe(node: ThatPrimaryNode): Boolean {
    return node.ip == "0.0.0.0" && node.port == PrimaryConfig.port
}

fun main() {
    connectToThatNode(ThatPrimaryNode("0.0.0.0", 9988))
}