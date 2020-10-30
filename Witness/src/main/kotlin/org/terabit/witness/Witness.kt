package org.terabit.witness

import org.terabit.core.Network


class Witness{
    private val privateKey = ByteArray(1024)
    private val publicKey = ByteArray(1024)
    private lateinit var network: Network
    fun stop(){
        //not necessary
    }
    fun start() {
        // parse public ip of primary nodes from ,
        // connect to one of them,
        // find the nearest new primary server(Using Kademlia)
        // connect to the new primary server.

        // wait for server event
    }
}

fun main(){
    val witness = Witness()
    witness.start()
}

