package org.terabit.tests.client

import org.ethereum.util.ByteUtil
import org.terabit.common.getSha256
import org.terabit.primary.TERABIT_JKS_FILE_NAME
import org.terabit.primary.TERABIT_JKS_FILE_PWD
import org.terabit.primary.TerabitJavaCodes
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

private var count = 40
private var name = TERABIT_JKS_FILE_NAME

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        try {
            count = args[0].toInt()
        } catch (e: Exception) {
        }
        try {
            name = args[1]
        } catch (e: Exception) {
        }
    }
    println("------------file=$name")

    val ks = KeyStore.getInstance("JKS")
    val file = File(name)
    if(file.exists()){
        ks.load(FileInputStream(file),TERABIT_JKS_FILE_PWD.toCharArray())
    }else{
        ks.load(null,null)
    }

    for (i in 0 until count) {
        val alias = "test$i"
        if (ks.containsAlias(alias)) {
            println("has alias: $alias")
            continue
        }
        val result = TerabitJavaCodes.createSelfSignedCert(name, TERABIT_JKS_FILE_PWD, alias, "111111")
        if(result.code != 0){
            println("Create account $i key failed, reason is: ${result.desc}")
            return
        }
        val pubKey = result.entry.certificate.publicKey.encoded
        println("--addr=${ByteUtil.toHexString(getSha256(pubKey))}")
    }
}
