package org.terabit.core.state

import org.terabit.db.LevelDbDataSource
import org.ethereum.datasource.Source
import org.ethereum.util.ByteUtil
import java.util.concurrent.ConcurrentHashMap

class CachedSource(val db: LevelDbDataSource) : Source<ByteArray, ByteArray> {
    private val storage = ConcurrentHashMap<ByteArray, Node>()

    override fun put(key: ByteArray, value: ByteArray) {
//        println("-----------------Cache put key=${ByteUtil.toHexString(key)}    thread:${Thread.currentThread().id}")
        val node = storage[key] ?: Node(value)
        node.value = value
        storage[key] = node
//        println("--Cache put key=${ByteUtil.toHexString(key)}    size:${storage.size}")
    }

    override fun get(key: ByteArray): ByteArray? {
        val node = storage[key]
        if (node != null) {
            return node.value
        }
        return db.get(key)
    }

    override fun delete(key: ByteArray) {
//        println("-----------------Cache delete key=${ByteUtil.toHexString(key)}")
        storage.remove(key)
    }

    override fun flush(): Boolean {
//        println("-------------------------------------------Cache flush------------------------------------")
        var hasDirty = false
        for (entry in storage.entries) {
//            println("----key: ${ByteUtil.toHexString(entry.key)}      dirty=${entry.value.dirty}")
            if (!entry.value.dirty) {
                continue
            }
            db.put(entry.key, entry.value.value)
            entry.value.dirty = false
            hasDirty = true
        }
//        println("-------------------------------------------Cache flush------------------------------------")
        return hasDirty
    }

    fun showCache() {
//        println("-------------------------------------------showCache------------------------------------")
        for (entry in storage.entries) {
            println("----showCache key: ${ByteUtil.toHexString(entry.key)}      dirty=${entry.value.dirty}")
        }
//        println("-------------------------------------------showCache------------------------------------")
    }

    private class Node(var value: ByteArray, var dirty: Boolean = true)
}