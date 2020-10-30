package org.terabit.primary.events

import org.terabit.primary.ThatPrimaryNode

class FindNodeEvent(ip: String, port: Int): ThatPrimaryNode(ip, port) {
    val nodeList = ArrayList<ThatPrimaryNode>()
}
