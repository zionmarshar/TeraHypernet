package org.terabit.primary.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.ReferenceCountUtil
import org.terabit.network.forward
private val shouldCompressTypes = arrayOf("text/html","text/css","application/javascript")
object HttpChannelInitializer : ChannelInitializer<NioSocketChannel>() {
    override fun initChannel(ch: NioSocketChannel?) {
        if(ch == null)
            return

        val pipe = ch.pipeline()
        with(pipe){
            addLast(object: SimpleChannelInboundHandler<Any>() {
                override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
//                    mslog.info("Uncaught channel exception:"+cause?.message)
//                    mslog.err("Uncaught channel exception:"+cause?.message)
                }
                override fun channelRead0(ctx: ChannelHandlerContext?, msg: Any?) {
                    ReferenceCountUtil.retain(msg)
                    ctx?.fireChannelRead(msg)
                }
            })

            addLast(HttpServerCodec())
            addLast(object: SimpleChannelInboundHandler<HttpRequest>(){
                override fun channelRead0(ctx: ChannelHandlerContext?, msg: HttpRequest?) {
                    if(ctx == null || msg == null)
                        return


                    val origin = msg.headers()[HttpHeaderNames.ORIGIN]
//                    ctx.channel().attr(LAST_REQUEST_ORIGIN).set(origin)
                    //every time before fire channel read need to call retain.
                    forward(ctx,msg)
                }

            })
            addLast(HttpObjectAggregator(5*1024*1024))
            addLast(object: HttpContentCompressor(){
                override fun beginEncode(headers: HttpResponse?, acceptEncoding: String?): Result? {
                    val ct = headers?.headers()?.get(HttpHeaderNames.CONTENT_TYPE)
                    return if(ct in shouldCompressTypes)
                        super.beginEncode(headers, acceptEncoding)
                    else
                        null
                }
            })
            addLast(ChunkedWriteHandler())
            addLast(FullHttpHandler())
        }
    }
}
