package org.terabit.core

class Network(private val me: IPrimary){
    val isEmpty: Boolean = true
//    private val kBucket = KBucket()
    //represent the status of the whole network.
    //block height.
    //total nodes(Primary)
    val committee = ArrayList<OtherPrimary>()
    //This is event driven, do not need network functions.

    fun broadcast(data: ByteArray) {
//        kBucket.broadCast(data)
    }
    //add this into kBucket. by level
    fun add(it: OtherPrimary) {
        //ping the remote node,make sure they are online.
        //setup real network connection
        if(!it.connect()) {
            println("can not connect to ${it.kid}")
            return
        }

        if(!it.ping()){
            println("can not ping to ${it.kid}")
            return
        }

//        kBucket.add(it)
    }

    fun initP2PNodes() {
//        kBucket.autoInit()
    }

    val clerkRequiredVotes: Int
        get() = 30
    val clerkIsReady: Boolean
        get() = false
    val clerkZeros: Int
        get() = 10
    val sealPrecedingThreshold: Int
        get() {return 10}
}