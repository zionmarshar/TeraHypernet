package org.terabit.primary.events

import org.terabit.core.sync.SyncHeader

class ListHeaderResultEvent(val list: List<SyncHeader>?, val height: Long)
