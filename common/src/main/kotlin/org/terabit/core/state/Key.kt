package org.terabit.core.state

class Key private constructor(private val data: ByteArray) {

    operator fun get(index: Int) = this.data[index]
    fun toByteArray(): ByteArray? {
        return data
    }
    companion object{
        fun fromByteArray(data:ByteArray):Key{
            //todo: is it good to do this by exception?
            if(data.size < 32)
                throw Exception("byte size must be 32")
            return Key(data)
        }
    }
}