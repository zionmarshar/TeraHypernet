package org.terabit.primary

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.terabit.common.PRIMARY_PORT
import org.terabit.common.base64Enc
import org.terabit.common.sleep
import org.terabit.core.Env
import org.terabit.core.myAccount
import org.terabit.primary.PrimaryConfig.connPrimaryIp
import org.terabit.primary.PrimaryConfig.connPrimaryPort
import org.terabit.primary.impl.connectToThatNode
import org.terabit.primary.impl.initEventBus
import org.terabit.primary.impl.initKBucket
import org.terabit.primary.impl.initMinor
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.*

const val PRIMARY_HTTP_PORT = 9501

var NodeId = 10000 //test id
var IsPrimary = true //test is primary
var PrimaryCount = 0 //test for minor

//make it run!
fun main(vararg args: String) {
    val parser = DefaultParser( )
    val options = Options()
    with(options){
        addOption("name", true, "Private key name")
        addOption("password", true, "Private key password, will override the one saved in config file if provided")
        addOption("config", true, "Config file name of primary node")
    }
    val cl = parser.parse( options, args )
    val configFileName = if(cl.hasOption("config")) cl.getOptionValue("config") else TERABIT_CONF_FILE_NAME

    PrimaryConfig.load(configFileName, cl.getOptionValue("name"), cl.getOptionValue("password"))

    initMyAccount()

    //primary mode
    if (PrimaryConfig.isPrimary) {
        initEventBus()

        //todo test
        if (NodeId != 10000) {
            PrimaryConfig.gasPrice += NodeId
            PrimaryConfig.gasPriceMethod1 += NodeId
            println("----gasPrice=${PrimaryConfig.gasPrice}   gasPriceMethod1=${PrimaryConfig.gasPriceMethod1}")
        }
        //todo test

        /**connect to network*/
        val cf = ThisPrimaryNode.init()
        cf?.addListener {
            ThisPrimaryNode.shutdown()
        }

        /**wait for requests*/
        //Do not need to merge this to ThisPrimaryNode, since http request is pretty limited and separated.
        val f = HttpServer.start(PrimaryConfig.httpPort)
        if(f?.isSuccess == true){
            f.channel().closeFuture().addListener {
                HttpServer.stop()
            }
        }else{
            HttpServer.stop()
        }

        initKBucket()
        return
    }

    if (Env.noMinor) {
        return
    }

    //minor mode
    //todo test, temporarily skip the addressing phase
    //ThisMinorNode.start(NodeId % PrimaryCount)
    val port = if (connPrimaryPort == null) {
        var p = NodeId % PrimaryCount
        if (p != PRIMARY_PORT) {
            p += PRIMARY_PORT
        }
        p
    } else {
        connPrimaryPort!!
    }
    initMinor()
    while (!connectToThatNode(connPrimaryIp, port)) {
        sleep(200)
    }

}

fun initMyAccount() {
    var alias = PrimaryConfig.jksItemAlias
    if (NodeId != 10000) {
        PrimaryConfig.isPrimary = IsPrimary
        alias += NodeId
    }
    PrimaryConfig.jksItemAlias = alias
    println("--------init account: alias=$alias")

    while (true) {
        val ks = KeyStore.getInstance("JKS")
        try{
            val file = File(TERABIT_JKS_FILE_NAME)
            if(file.exists()){
                ks.load(FileInputStream(file),TERABIT_JKS_FILE_PWD.toCharArray())
            }else{
                ks.load(null,null)
            }
        }catch (e:Exception){
            throw Exception("Open key store failed reason= ${e.localizedMessage}")
        }

        val proPass = KeyStore.PasswordProtection(PrimaryConfig.jksItemPwd.toCharArray())
        val pkEntry: KeyStore.PrivateKeyEntry
        try{
            pkEntry = ks.getEntry(alias, proPass) as KeyStore.PrivateKeyEntry

            myAccount.uuid = UUID.randomUUID().toString()
            myAccount.pswHash = PrimaryConfig.jksItemPwd
            myAccount.priKeyBytes = pkEntry.privateKey.encoded
            myAccount.priKey = base64Enc(myAccount.priKeyBytes)
            myAccount.pubKeyBytes = pkEntry.certificate.publicKey.encoded
            break
        } catch (e:Exception){
            println("Can not retrieve terabit cert from jks entry")
        }
        val result = TerabitJavaCodes.createSelfSignedCert(TERABIT_JKS_FILE_NAME,
                TERABIT_JKS_FILE_PWD, alias, PrimaryConfig.jksItemPwd)
        if(result.code != 0){
            println("Create account $alias key failed, reason is: ${result.desc}")
            return
        }
        sleep(200)
    }


    println("--------init account: addr=${myAccount.getAddr()}")
//    println("--------init account: pri key=${ByteUtil.toHexString(myAccount.priKeyBytes)}")
//    println("--------init account: pub key=${ByteUtil.toHexString(myAccount.pubKeyBytes)}")
}
