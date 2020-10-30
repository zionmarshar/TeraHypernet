package org.terabit.primary.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.terabit.primary.ThatPrimaryNode
import org.ethereum.util.ByteUtil

class ThatPrimaryNodeAdapter: TypeAdapter<ThatPrimaryNode>() {
    override fun write(out: JsonWriter, value: ThatPrimaryNode) {
        out.beginObject()
        out.name("ip").value(value.ip)
        out.name("port").value(value.port)
        out.name("kid").value(ByteUtil.toHexString(value.kid))
        out.endObject()
    }

    override fun read(json: JsonReader): ThatPrimaryNode {
        json.beginObject()
        val node = ThatPrimaryNode(json.nextString(), json.nextInt())
        node.kid = ByteUtil.hexStringToBytes(json.nextString())
        json.endObject()
        return node
    }
}