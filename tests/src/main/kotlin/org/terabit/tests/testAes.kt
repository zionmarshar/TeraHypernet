package org.terabit.tests

import org.terabit.common.aesDec
import org.terabit.common.aesEnc

fun main(){
    val pwd = "123456"
    val content = "abcdefgvery very long"
    val enced = aesEnc(content, pwd)
    println(enced)
    val deced = aesDec(enced,pwd)
    println(String(deced))
}