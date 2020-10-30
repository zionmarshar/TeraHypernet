package org.terabit.tests

fun info(lab: String, msg: String) {
    println("$lab info: $msg")
}

fun error(lab: String, msg: String) {
    System.err.println("$lab error: $msg")
}