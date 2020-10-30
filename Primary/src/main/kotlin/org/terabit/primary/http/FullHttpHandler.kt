package org.terabit.primary.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import org.terabit.common.Log
import org.terabit.core.MsHttpRequest
import org.terabit.core.base.TerabitData

import org.terabit.network.replyHttpOption
import org.terabit.network.sendCode
import org.terabit.network.sendFile
import org.terabit.primary.httpApis
import org.terabit.primary.impl.kBucket
import java.io.File
import java.net.URLDecoder
import java.util.*

private val mslog = Log("FullHttpHandler")

class FullHttpHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: FullHttpRequest?) {
        if (ctx == null || msg == null)
            return

        val path = msg.uri()
        if (path == null) {

        }

        if (msg.method == HttpMethod.OPTIONS) {
            replyHttpOption(ctx, null)
            return
        }


        val questionMarkIndex = path.indexOf("?")
        val pageName = if (questionMarkIndex == -1) {
            path
        } else {
            path.substring(0, questionMarkIndex)
        }
        //println("----------------call api: $pageName")
        val api = httpApis[pageName.toLowerCase()]
        if (api != null) {
            mslog.info("FullHttp api request to api$pageName")
            val msRequest = MsHttpRequest(msg);

            //val user = users["test"]
//            val clientToken = msRequest.getCookie("token");
            //mslog.info("clientToken=$clientToken")
//            val userToken: UserToken? = userTokenManager.tryGetUserToken(clientToken);
//            mslog.info("userToken=$userToken");
//            if (api.canBeCalledByUser(userToken?.user)) {
//                mslog.info("api[$api] can be executed")
//                try {
//                    api.onRequest(ctx, msRequest, userToken?.user)
////                    mslog.info("FullHttp api request end$pageName")
//                    return
//                } catch (e: Exception) {
//                    mslog.err("http api exception for $pageName", e)
//                    return sendCode(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.message)
//                }
//            } else {
//                mslog.err("http api forbidden")
//                return sendCode(ctx, HttpResponseStatus.FORBIDDEN)
//            }
            api.onRequest(ctx,msRequest,null)
        }else{
            sendCode(ctx, HttpResponseStatus.NOT_FOUND)
//            provideHttpFile(ctx, pageName, msg)
        }


    }

    private fun provideHttpFile(ctx: ChannelHandlerContext, path: String, httpRequest: FullHttpRequest) {
        val WEBROOT = "./"
        //ServerUtil.println("---------provideHttpFile=" + holder.getPath());
        var fileName = path

        fileName = if (path == "/") {
            "index.html"
        } else {
            URLDecoder.decode(fileName, "utf-8")
        }
        val appRoot = File("./")
        val filePath = if(path.startsWith("/upload")
            || path.startsWith("/.well-known"))
        {
            "${appRoot.absolutePath}$fileName"
        }else{
            "${WEBROOT}/$fileName"
        }
        val file = File(filePath)
        val absFilePath = file.absolutePath
        if (!absFilePath.startsWith(appRoot.absolutePath) && !absFilePath.startsWith(File(WEBROOT).absolutePath)) {
            sendCode(ctx, HttpResponseStatus.FORBIDDEN)
            return
        } else {
            if (!file.exists()) {
                if (!path.startsWith("/assets")
                    && !path.startsWith("/upload")
                    ) {
                    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                    mslog.err("Request path not found, redirect to index.html xxxxxxxx $filePath")
                    sendFile(ctx, httpRequest, response, "$WEBROOT/index.html")
                }else{
                    sendCode(ctx, HttpResponseStatus.NOT_FOUND)
                }
            }
        }

        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        sendFile(ctx, httpRequest, response, filePath)
    }
}

fun setClientCookie(resp: DefaultHttpResponse, name: String, value: String) {
    val cookie = DefaultCookie(name, value);
    cookie.path = "/"
    cookie.isHttpOnly = true
    cookie.isSecure = false
    resp.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie))
}


fun deleteClientCookie(resp: DefaultHttpResponse, name: String) {
    val cookie = DefaultCookie(name, "");
    cookie.path = "/"
    cookie.isHttpOnly = true
    cookie.isSecure = false
    resp.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie))
}

fun broadFromApi(data: TerabitData) {
    val uuid = UUID.randomUUID().toString()
    kBucket.recordBroadMsg(uuid)
    kBucket.sendBroadcast(data, uuid)
}