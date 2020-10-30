import org.terabit.common.asymDecrypt
import org.terabit.common.asymEncrypt
import org.terabit.primary.TerabitJavaCodes
import java.security.cert.X509Certificate

fun test1(){
    val result = TerabitJavaCodes.createSelfSignedCert("terabit.jks","abc1","pwd123","1111")
    if(result.code != 0) {
        println("CreateSelfSingedCert Failed, reason is ${result.desc}")
        return
    }

    val ent = result.entry
    val cert = ent.certificate as X509Certificate
    val pub = cert.publicKey.encoded
    val priv = ent.privateKey.encoded

    val dd = asymEncrypt(priv,"acbde".toByteArray())
    println("decrypted="+String(asymDecrypt(pub,dd)))
}

fun test2(){

}
class Node{
    var type = 1
}
fun main(){
//    val c = (n = Node()).type


}