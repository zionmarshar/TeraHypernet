package org.terabit.core.base

import org.ethereum.trie.TrieImpl
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.ethereum.util.RLP
import org.ethereum.util.RLPList
import org.terabit.common.base64Dec
import kotlin.collections.ArrayList

//all implementation classes need to implement a RLPList parameter constructor
interface TerabitData {
    fun getHash(): ByteArray
    fun encode(): ByteArray
    fun verify(vararg data: Any): Boolean
}

fun encodeList(list: List<TerabitData>): ByteArray {
    val ts = arrayOfNulls<ByteArray>(list.size)
    for ((i, t) in list.withIndex()) {
        ts[i] = t.encode()
    }
    return RLP.encodeList(*ts)
}

fun <T: TerabitData> decodeList(data: ByteArray, cls: Class<T>): ArrayList<T> {
    val list = ArrayList<T>()
    try {
        val rlp = RLP.decodeList(data)
        for (e in rlp) {
            list.add(decodeTeraData<T>(e.rlpData, cls))
        }
    } catch (e: Exception) {
    }
    return list
}

fun <T: TerabitData> decodeTeraData(data: ByteArray, cls: Class<T>): T {
    val cs = cls.getConstructor(RLPList::class.java)
    val rpl = RLP.decodeList(data)
    return cs.newInstance(rpl)
}

fun <T: TerabitData> decodeTeraData(base64: String, cls: Class<T>): T {
    val bytes = base64Dec(base64)
    return decodeTeraData(bytes, cls)
}

fun getListRootHash(list: List<TerabitData>?): ByteArray {
    if (list == null) {
        return EMPTY_BYTE_ARRAY
    }
    val trie = TrieImpl()
    for ((i,data) in list.withIndex()) {
        trie.put(RLP.encodeInt(i), data.encode())
    }
    return trie.rootHash
}