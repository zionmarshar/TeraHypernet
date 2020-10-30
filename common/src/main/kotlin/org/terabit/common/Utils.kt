package org.terabit.common

import org.ethereum.util.ByteUtil
import org.terabit.core.Credential
import java.util.*

const val PRIMARY_PORT  = 9000

const val NA_COIN = 1L
const val K_NA = 1000 * NA_COIN //pow(10, 3)
const val M_NA = 1000 * K_NA
const val G_NA = 1000 * M_NA
const val TERA_COIN = 100 * G_NA //pow(10, 11)


/********************** contract size **********************/
const val CONTRACT_VERSION_LENGTH = 2
const val CONTRACT_METHOD_LENGTH = 2
const val CONTRACT_METHOD_1_DATE_LENGTH = 32
/********************** contract information **********************/
const val CONTRACT_VERSION_INIT: Short = 1
/********************** contract method **********************/
const val CONTRACT_METHOD_APPLY_FOR_ACTIVATION: Short = 1 //set the price for application through gas price, amount not used
/********************** contract setting **********************/
const val CONTRACT_ACTIVATION_VALID_PERIOD = 600 * 24 * 60 * 60L //600 days (unit: s)


const val ADDRESS_SIZE = 32 //address byte size

//block
const val BLOCK_EXTRA_DATA_SIZE = 64 //bytes
const val BLOCK_GAS_LIMIT_MIN = 1000000L  //100w
const val BLOCK_GAS_LIMIT_MAX = 20000000L //2000w
const val BLOCK_GAS_LIMIT_STEP = 10000L   //1w
const val BLOCK_GAS_LIMIT_TRIGGER_LOWER = 10.0 //percentage; can reduce
const val BLOCK_GAS_LIMIT_TRIGGER_UPPER = 90.0 //percentage; can increase

//gas
const val GAS_ZERO = 0
const val GAS_TRANSACTION = 21000
const val GAS_TX_CREATE = 32000  //create contract
const val GAS_V1_SIMPLE_STORAGE = 500  //contract V1 storage price per byte

//transaction
const val TX_DATA_SIZE_MAX = 32 * 1024 //transaction data max size, 32K bytes

const val DEFAULT_DIFFICULTY = 8L //leading 0 number
val BLOCK_REWARD_COUNT = doubleArrayOf(10.0, 5.0, 2.5, 1.2, 0.6, 0.3, 0.2, 0.1, 0.1) //TERA_COIN
const val BLOCK_REWARD_PHASE = 1000000

fun compareTo(bi1:ByteArray, bi2:ByteArray):Int{
    val (longArray, shortArray) = orderByArrLength(bi1,bi2)
    val index = longArray.size - shortArray.size
    for(i in 0 until index){
        if(longArray[i] != 0.toByte())
            return 1
    }
    for(i in index until longArray.size){
        val b1 = longArray[i]
        val b2 = shortArray[i-index]
        if(b1 != b2)
            return b1 - b2
    }
    return 0
}

fun orderByArrLength(bi1:ByteArray, bi2:ByteArray):Pair<ByteArray,ByteArray>{
    val shortArray:ByteArray
    val longArray:ByteArray
    if(bi1.size >= bi2.size){
        longArray = bi1
        shortArray = bi2
    }else{
        longArray = bi2
        shortArray = bi1
    }
    return Pair(longArray,shortArray)
}

fun sleep(ms: Long) {
    try {
        Thread.sleep(ms)
    } catch (e: Exception) {
    }
}

fun createRoundHash(priKey: ByteArray, pubKey:ByteArray, leadZeroCount: Long,
                    originHash: ByteArray, over: ()->Boolean): Credential? {
    //val time = System.nanoTime()
    val data = ByteArray(8 + originHash.size)
    System.arraycopy(originHash, 0, data, 8, originHash.size)
    var round = 1L
    while (true) {
        val roundBytes = round.bytes()
        System.arraycopy(roundBytes, 0, data, 0, 8)
        val signedBytes = sign(priKey, data)
        val signedHash = getSha256(signedBytes)
        //println("----------signedHash=${ByteUtil.toHexString(signedHash)}   round=$round   time=${System.nanoTime()}")
        if (ByteUtil.numberOfLeadingZeros(signedHash) >= leadZeroCount) {
            return Credential(signedBytes, pubKey, round)
        }
        if (over()) {
            break
        }
        round++
    }
    //println("----------times=${System.nanoTime() - time}")
    return null
}

fun verifyRoundHash(signedBytes:ByteArray, pubKey:ByteArray, round: Long, leadZeroCount: Long,
                    originHash: ByteArray): Boolean {
    if (signedBytes.isEmpty() && pubKey.isEmpty()) {
        return true
    }
    //verify leading 0 number
    //println("-----------signedBytes=${ByteUtil.toHexString(signedBytes)}")
    val minorHash = getSha256(signedBytes)
    //println("-----------minorHash=${ByteUtil.toHexString(minorHash)}")
    if (ByteUtil.numberOfLeadingZeros(minorHash) < leadZeroCount) {
        return false
    }
    //round
    val roundHash = asymDecrypt(pubKey, signedBytes)
    //println("-----------roundHash=${ByteUtil.toHexString(roundHash)}")
    if (!Arrays.equals(round.bytes(), 0, 8, roundHash, 0, 8)) { //round
        return false
    }
    //hash
    val size = originHash.size
    return Arrays.equals(originHash, 0, size, roundHash, 8, 8 + size)
}

fun pow(m: Long, n: Int): Long {
    var result = 1L
    for (i in 0 until n) {
        result *= m
    }
    return result
}

fun curTime():Long = System.currentTimeMillis()

fun curSecond():Long = System.currentTimeMillis() / 1000