package org.terabit.primary.events

import org.terabit.primary.ThatPrimaryNode

class PingEvent(val isPrimary: Boolean, ip: String, port: Int): ThatPrimaryNode(ip, port)
