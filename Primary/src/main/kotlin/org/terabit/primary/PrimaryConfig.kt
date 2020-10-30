package org.terabit.primary

import org.terabit.common.PRIMARY_PORT
import org.terabit.core.Env
import java.io.File
import java.io.FileInputStream
import java.util.*

object PrimaryConfig{
    var httpPort: Int = PRIMARY_HTTP_PORT
        private set
    private var loaded = false
    lateinit var jksItemPwd: String  private set
    var port: Int = PRIMARY_PORT
        private set
    lateinit var jksFileName:String  private set
    lateinit var  jksFilePwd:String private set
    lateinit var  jksItemAlias:String

    //tx
    var gasPrice = 1L //miner's minimum gas price limit
    var gasPriceMethod1 = 2L //miner's minimum gas price limit for activation contract, must bigger than gasPrice
    var txMaxCount = 10000  //transaction queue total max count


    var isPrimary = true

    //primary notes
    var seedIp = "0.0.0.0"

    //minor notes
    var minorCount = 10
    var connPrimaryIp = "0.0.0.0"
    var connPrimaryPort: Int? = null


    fun load(cfgName: String, itemName: String?, itemPwd: String?){
        if(loaded)
            throw Exception("Config already loaded")

        val file = File(cfgName)
        val properties = Properties()
        if(file.exists())
        {
            val istream = FileInputStream(cfgName)
            properties.load(istream)
        }
        this.jksItemAlias = itemName ?: properties.getProperty("node_alias")?: TERABIT_JKS_ITEM_ALIAS
        this.jksItemPwd   = itemPwd ?: (properties.getProperty("node_pwd")?: TERABIT_JKS_ITEM_PWD)
        this.port         = properties.getProperty("port")?.toInt() ?: PRIMARY_PORT
        this.httpPort     = properties.getProperty("http_port")?.toInt() ?: PRIMARY_HTTP_PORT

        //tx
        this.gasPrice     = properties.getProperty("gas_price")?.toLong() ?: gasPrice
        this.gasPriceMethod1 = properties.getProperty("gas_price_for_activation")?.toLong() ?: gasPriceMethod1
        this.txMaxCount   = properties.getProperty("tx_max_count")?.toInt() ?: txMaxCount


        this.isPrimary    = properties.getProperty("primary")?.toBoolean() ?: true
        if (Env.noMinor) {
            this.isPrimary = true
        }

        //primary notes
        this.seedIp   = properties.getProperty("seed_ip") ?: this.seedIp

        //minor notes
        this.minorCount     = properties.getProperty("minor_count")?.toInt() ?: minorCount
        this.connPrimaryIp   = properties.getProperty("conn_primary_ip") ?: this.connPrimaryIp
        this.connPrimaryPort = properties.getProperty("conn_primary_port")?.toInt()

        //modify listen port for test
        if (NodeId != 10000) {
            this.port += NodeId
            this.httpPort += NodeId
        }
    }
}