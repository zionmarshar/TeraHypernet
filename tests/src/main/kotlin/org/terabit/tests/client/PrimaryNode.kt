package org.terabit.tests.client

import org.terabit.primary.IsPrimary
import org.terabit.primary.NodeId
import org.terabit.primary.PrimaryCount
import org.terabit.primary.main as main1

fun main(vararg args: String) {
    try {
        NodeId = args[0].toInt()
    } catch (e: Exception) {
        println("************No node id************")
        return
    }
    try {
        IsPrimary = args[1].toBoolean()
    } catch (e: Exception) {
        println("************No primary set************")
        return
    }
    try {
        PrimaryCount = args[2].toInt()
    } catch (e: Exception) {
        println("************No primary count************")
        return
    }
    main1(*args)
}