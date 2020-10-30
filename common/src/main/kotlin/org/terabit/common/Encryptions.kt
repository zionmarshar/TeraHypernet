package org.terabit.common

import io.netty.buffer.Unpooled
import io.netty.util.CharsetUtil
import org.ethereum.util.ByteUtil
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec


const val RSA_ALG = "RSA/ECB/PKCS1Padding"
const val AES_ALG = "AES/ECB/PKCS5padding" //notice must be set for different vm
private const val RSA_KF = "RSA"
private const val KEYSIZE = 2048

fun initAesCipher(password:String, mode:Int):Cipher{
    val kgen = KeyGenerator.getInstance("AES")
    val random = SecureRandom.getInstance("SHA1PRNG")
    random.setSeed(password.toByteArray())
    kgen.init(128, random)
    val secretKey = kgen.generateKey()
    val enCodeFormat = secretKey.encoded
    val key = SecretKeySpec(enCodeFormat, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(mode, key)
    return cipher
}
fun aesEnc(content:String,password:String):ByteArray{
    val cipher = initAesCipher(password,Cipher.ENCRYPT_MODE)
    val byteContent: ByteArray = content.toByteArray()
    val bytes = cipher.doFinal(byteContent)
    return bytes
}
fun aesDec(content:ByteArray,password:String):ByteArray{
    val cipher = initAesCipher(password,Cipher.DECRYPT_MODE)
    val bytes = cipher.doFinal(content)
    return bytes
}
fun base64Dec(s:String):ByteArray{
    val buf = Unpooled.wrappedBuffer(s.toByteArray(CharsetUtil.US_ASCII))
    val buf1 = io.netty.handler.codec.base64.Base64.decode(buf)
    val bytes = ByteArray(buf1.readableBytes())
    buf1.readBytes(bytes)
    buf1.release()
    buf.release()
    return bytes
}

fun base64Enc(bytes:ByteArray?):String{
    val buf = Unpooled.wrappedBuffer(bytes)
    val buf1 = io.netty.handler.codec.base64.Base64.encode(buf, 0,buf.readableBytes(),false)
    val s = buf1.toString(CharsetUtil.US_ASCII)
    buf1.release()
    buf.release()
    return s
}

fun getAsymKey(bytes:ByteArray?, isPub:Boolean): Key {
    lateinit var keySpec: EncodedKeySpec
    val keyFactory = KeyFactory.getInstance(RSA_KF)
    return if(isPub){
        keySpec = X509EncodedKeySpec(bytes)
        val k = keyFactory.generatePublic(keySpec)
        k
    }else{
        keySpec = PKCS8EncodedKeySpec(bytes)
        keyFactory.generatePrivate(keySpec)
    }
}



fun sign(privBytes:ByteArray?,data:ByteArray):ByteArray{
    return asymEncrypt(privBytes,data)
}

fun asymEncrypt(privKey:ByteArray?, data:ByteArray, keyIsPub:Boolean = false):ByteArray{
    val key = getAsymKey(privKey,keyIsPub)
    val cipher = Cipher.getInstance(RSA_ALG)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

fun asymDecrypt(pubBytes:ByteArray?, data:ByteArray, keyIsPub:Boolean = true):ByteArray{
    val key = getAsymKey(pubBytes,keyIsPub)
    val cipher = Cipher.getInstance(RSA_ALG)
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(data)
}

fun rsaKeyPair(): KeyPair? {
    val mslog = Log("BinaryKP")
    mslog.info("start")
    val secureRandom = SecureRandom()
    val keyPairGenerator = KeyPairGenerator.getInstance(RSA_KF)
    keyPairGenerator.initialize(KEYSIZE, secureRandom)
    val keyPair = keyPairGenerator.generateKeyPair()
    return keyPair
}

fun getSha256(data: ByteArray): ByteArray {
    var result = ByteUtil.EMPTY_BYTE_ARRAY
    try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(data)
        result = messageDigest.digest()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

fun getSha256(data: ByteArray, start: Int, length: Int): ByteArray {
    var result = ByteUtil.EMPTY_BYTE_ARRAY
    try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(data, start, length)
        result = messageDigest.digest()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

fun getSha256(vararg data: ByteArray?): ByteArray {
    var result = ByteUtil.EMPTY_BYTE_ARRAY
    try {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        for (d in data) {
            if (d == null || d.isEmpty()) {
                continue
            }
            messageDigest.update(d)
        }
        result = messageDigest.digest()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

fun Long.bytes() = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(this).array()
fun ByteArray.toLong() = BigInteger(1, this).toLong()