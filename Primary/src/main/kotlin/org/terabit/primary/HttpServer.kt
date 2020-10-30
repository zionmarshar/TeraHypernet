package org.terabit.primary

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.terabit.primary.http.HttpChannelInitializer
import org.terabit.primary.http.MsWebApi
import org.terabit.primary.http.api.VerApi
import org.terabit.server.HttpApi
import org.reflections.Reflections
import java.util.LinkedHashMap

val httpApis = LinkedHashMap<String, HttpApi>()
object HttpServer {
    private val mBoss = NioEventLoopGroup() //for listen
    private val mWorker = NioEventLoopGroup(32) //for processing i/o of each socket/channel

    fun start(port: Int = PRIMARY_HTTP_PORT):ChannelFuture?{
        val bs = ServerBootstrap()
        try {
            bs.group(
                mBoss,
                mWorker
            )
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(HttpChannelInitializer)

            val f = bs.bind(port).sync()
            if (f.isSuccess) {
                prepareHttpApis(
                    VerApi::class.java.`package`.name,
                    "api"
                )
                println("************ Terabit http api on port $port ************")
            } else {
                println("************ Terabit http api failed on $port ************")
            }
            return f
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    fun stop(){
        mWorker.shutdownGracefully()
        mBoss.shutdownGracefully()
    }
}

fun prepareHttpApis(apiPackageName: String, apiBasePath: String) {
    val refs = Reflections(apiPackageName)
    val classes = refs.getSubTypesOf(HttpApi::class.java)
    var callback: HttpApi
    for (cls in classes) {
        val api = cls.getAnnotation(MsWebApi::class.java) ?: continue
        var name = api.name
        if (name.isEmpty()) {
            name = cls.simpleName.toLowerCase()
        }
        val thisApiPackage = cls.`package`.name
        val subPackage =
            if (thisApiPackage == apiPackageName) "" else thisApiPackage.replace("$apiPackageName.", "") + "/"
        name = "/$apiBasePath/$subPackage$name"
//        mslog.err("final name=$name")
        try {
            callback = cls.getConstructor().newInstance() as HttpApi
        } catch (e: Exception) {
            continue
        }

        callback.setMethod(api.method.trim { it <= ' ' }.toUpperCase())
        httpApis[name] = callback
    }
}