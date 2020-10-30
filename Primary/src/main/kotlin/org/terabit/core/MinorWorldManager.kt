package org.terabit.core

import org.terabit.common.createRoundHash
import org.terabit.primary.ThatPrimaryNode
import org.terabit.primary.events.ActiveEvent
import org.terabit.primary.events.InactiveEvent
import org.terabit.primary.events.PingEvent
import org.terabit.primary.events.StopSealEvent
import org.greenrobot.eventbus.Subscribe
import org.terabit.core.base.TerabitData
import org.terabit.primary.impl.createPingCmd
import java.util.*

class MinorWorldManager {
    private var primaryNode: ThatPrimaryNode? = null
    private var curHeader: BlockHeader? = null

    fun start() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                doTera()
            }
        }, 200, 100)
    }

    fun doTera() {
        if (primaryNode == null) {
            return
        }
        val header = curHeader ?: return
        println("--------start check header: $header")
        val sign = createRoundHash(myAccount.priKeyBytes, myAccount.pubKeyBytes, credentialDifficulty, header.getHash()) {
            curHeader == null
        } ?: return
        println("--------createRoundHash in round ${sign.round}")
        sign.blockHeight = header.height
        sign.isMinorSeal = true
        primaryNode?.send(sign)
        curHeader = null
    }

    @Subscribe
    fun onActive(event: ActiveEvent) {
        println("--------minor--onActive(): $event")

        //send ping
        event.sendCmd(createPingCmd())
    }

    @Subscribe
    fun onInactive(event: InactiveEvent) {
        println("--------minor--onInactive(): $event")
        primaryNode = null
    }

    @Subscribe
    fun onPing(event: PingEvent) {
        println("--------minor--onPing: $event")
        primaryNode = event
    }

    @Subscribe
    fun onStopSeal(event: StopSealEvent) {
        println("--------minor--stop seal")
        curHeader = null
    }

    @Subscribe
    fun onTerabitData(data: TerabitData) {
        when (data) {
            is BlockHeader -> {
                println("--------recv header: ${data.getHash()}")
                if (!data.verify()) {
                    println("--------header verify failed")
                    return
                }
                println("--------verify success")
                if (data.minorSign.isNotEmpty()) { //has signed
                    curHeader = null
                    return
                }
                curHeader = data
            }
        }
    }
}