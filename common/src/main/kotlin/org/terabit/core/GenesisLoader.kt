package org.terabit.core

import com.google.gson.JsonObject
import org.terabit.common.DEFAULT_DIFFICULTY
import org.ethereum.util.ByteUtil
import org.terabit.common.BLOCK_GAS_LIMIT_MIN
import org.terabit.common.getSha256
import java.util.*

object GenesisLoader {

    fun createBlockForJson(genesisJson: JsonObject): FinalBlock {
        val parentHash = ByteUtil.hexStringToBytes(genesisJson.get("parentHash").asString)
        val miner = ByteUtil.hexStringToBytes(genesisJson.get("miner").asString)
        val timestamp = genesisJson.get("timestamp").asLong
        val extraData = ByteUtil.hexStringToBytes(genesisJson.get("extraData").asString)
        return FinalBlock(parentHash, miner, timestamp, 0L, DEFAULT_DIFFICULTY,
                BLOCK_GAS_LIMIT_MIN, extraData, null)
    }

    fun createGenesis(): FinalBlock {
        val parentHash = ByteUtil.hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000")
        val miner = getSha256("Terabit000000001".toByteArray())
//        val cal = Calendar.getInstance()
//        cal.set(2020, Calendar.OCTOBER, 1, 9, 0, 0)
//        val timestamp = cal.timeInMillis / 1000
        val timestamp = 1600000000L
        val extraData = "Terabit".toByteArray()

        return FinalBlock(parentHash, miner, timestamp, 0L, DEFAULT_DIFFICULTY,
                BLOCK_GAS_LIMIT_MIN, extraData, null)
    }
}