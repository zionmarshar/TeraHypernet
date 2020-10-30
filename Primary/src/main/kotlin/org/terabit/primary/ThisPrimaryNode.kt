package org.terabit.primary

import io.netty.channel.ChannelFuture
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.terabit.primary.impl.waitForNetworkConnectionAndEvents

const val TERABIT_CONF_FILE_NAME = "terabit.conf"
const val TERABIT_JKS_FILE_PWD = "terabit_pwd"
const val TERABIT_JKS_ITEM_PWD = "terabit_jks_item_pwd"
const val TERABIT_JKS_ITEM_ALIAS = "terabit_jks_item_alias"
const val TERABIT_JKS_FILE_NAME = "terabit.jks"

object ThisPrimaryNode {
    private var initialized = false
    //change this value when total primary miners changed.
    //these settings will be update by network event.
    private var maxBookKeeperCount = 30

    fun init(): ChannelFuture? {
        if(initialized)
            throw Exception("Primary node has been initialized")

        EventBus.getDefault().register(this)
        return waitForNetworkConnectionAndEvents(PrimaryConfig.port)
    }

    fun shutdown() {

    }

    @Subscribe
    fun onQuit(node:PrimaryNode){
        updatePrimaryNetwork()
    }
    @Subscribe
    fun onJoin(node:PrimaryNode){
        updatePrimaryNetwork()
    }

    private fun updatePrimaryNetwork() {
        maxBookKeeperCount = 30
    }
}