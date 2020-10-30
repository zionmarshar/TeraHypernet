package org.terabit.core.state

import org.terabit.db.LevelDbDataSource
import org.terabit.db.StateObject
import org.ethereum.crypto.HashUtil
import org.ethereum.trie.TrieImpl
import org.ethereum.util.ByteUtil
import java.io.Closeable

class StateDb: Closeable {
    companion object {
        private lateinit var db: LevelDbDataSource

        fun init(dbName: String) {
            db = LevelDbDataSource(dbName)
        }

        fun load(stateRoot: ByteArray): StateDb {
            val state = StateDb()
            state.setRoot(stateRoot)
            return state
        }
    }

    private val map = HashMap<ByteArray, StateObject>()
    private val trie = TrieImpl(CachedSource(db))

    fun getStateObject(key: ByteArray): StateObject? {
        val obj = map[key]
        if (obj != null) {
            return obj
        }
        val bytes = trie.get(key) ?: return null
        return StateObject(bytes)
    }

    private fun addStateObject(key: ByteArray, value: StateObject) {
        map[key] = value
        trie.put(key, value.encode())
    }

    fun modifyCoin(obj: StateObject, amount: Long) {
        //println("----------------------modifyCoin()----${ByteUtil.firstToHexString(obj.address)}")
        val n = obj.withAddAmount(amount)
        addStateObject(obj.address, n)
    }

    fun update(obj: StateObject) {
        addStateObject(obj.address, obj)
    }

    fun findOrCreateAccount(address: ByteArray): StateObject {
        var obj = getStateObject(address)
        if (obj == null) {
            obj = StateObject(address, 0, 0)
            addStateObject(obj.address, obj)
        }
        return obj
    }

    fun setRoot(root: ByteArray) {
        map.clear()
        if (root.contentEquals(HashUtil.EMPTY_TRIE_HASH)) {
            return
        }
        trie.setRoot(root)
    }

    fun getRoot(): ByteArray {
        return trie.rootHash
    }

    fun flush(): Boolean {
        return trie.flush()
    }

    override fun close() {
        db.close()
    }

    //TODO test
    fun showNode() {
        val addr1 = ByteUtil.hexStringToBytes("58e487d134c4457926b7cf5eef6e0b1ad15f24511da36b7b483c12fdab764b50")
        val addr2 = ByteUtil.hexStringToBytes("504dd78453bf9b15fde49dd1225884b5d0a68c093e3c3a6074dc530160e809bc")
        val addr3 = ByteUtil.hexStringToBytes("c10aa7a1c67784a394a2ef4c92e802c4a68402f6834b930673c89e302513db17")
        val addr4 = ByteUtil.hexStringToBytes("d46f3a96ab6e10cd4e5e63b852ca8f364448ff8608668db6e495cd96edbee97e")
        println("-----------------------------showNode()--root=${ByteUtil.toHexString(trie.rootHash)}")
        println("------------addr0=${getStateObject(addr1)}")
        println("------------addr1=${getStateObject(addr2)}")
        println("------------addr2=${getStateObject(addr3)}")
        println("------------addr3=${getStateObject(addr4)}")
    }
}