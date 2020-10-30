package org.terabit.wallet

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ethereum.util.ByteUtil
import org.json.JSONObject
import org.terabit.common.base64Enc
import org.terabit.core.base.TerabitData
import org.terabit.core.Transaction
import org.terabit.core.base.decodeTeraData
import org.terabit.db.Activation
import org.terabit.db.StateObject
import org.terabit.network.ApiCode
import java.util.concurrent.TimeUnit

object Net {
    private val client = OkHttpClient()
    private val clientLong = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    private fun requestJson(request: Request, useLong: Boolean = false): JSONObject? {
        try {
            val c = if (useLong) clientLong else client
            val response = c.newCall(request).execute()
            if (!response.isSuccessful) {
                return null
            }
            val body = response.body?.string() ?: return null
            println("----json body: $body")
            return JSONObject(body)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getJson(url: String, useLong: Boolean = false): JSONObject? {
        val request = Request.Builder().url(url).build()
        return requestJson(request, useLong)
    }

    fun <T: TerabitData> getTerabitData(url: String, cls: Class<T>): T? {
        val json = getJson(url) ?: return null
        if (json.optInt("code") != ApiCode.SUCCESS.code) {
            return null
        }
        try {
            return decodeTeraData(json.optString("data", null), cls)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getAccount(address: ByteArray): StateObject? {
        val addr = ByteUtil.toHexString(address)
        val url = "http://127.0.0.1:9501/api/account/get?addr=$addr"
        return getTerabitData(url, StateObject::class.java)
    }

    fun getActivation(address: ByteArray): Activation? {
        val addr = ByteUtil.toHexString(address)
        val url = "http://127.0.0.1:9501/api/kt/get_activation?addr=$addr"
        return getTerabitData(url, Activation::class.java)
    }

    fun sendTx(tx: Transaction): JSONObject? {
        val body = base64Enc(tx.encode()).toRequestBody()
        val request = Request.Builder().url("http://127.0.0.1:9501/api/tx/send").post(body).build()
        return requestJson(request)
    }
}