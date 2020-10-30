package org.terabit.core


import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.terabit.common.base64Enc
import org.terabit.primary.*
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.time.Duration
import java.util.*
import kotlin.system.exitProcess


//using netty to connect to localhost(by default), or a remote primary miner by url
fun main(args:Array<String>){
    val parser = DefaultParser( )
    val options = Options()
    with(options){
        addOption("help", false, "Print this usage information")
        addOption("transfer", false, "Transfer terabit coin to an address")
        addOption("from", true, "from account")
        addOption("to", true, "to account")
        addOption("amount", true, "amount")
        addOption("sign", true, "sign of private keys for this transaction")
        addOption("create_account", false, "create account")
        addOption("password", true, "account password")
        addOption("name", true, "account display name")
    }

    var reqCmd = "get_info"
    val cl = parser.parse( options, args )
    with(cl){
        when{
            hasOption("create_account")->{
                if(hasOption("password") && hasOption("name")){
                    val alias = getOptionValue("name")
                    val pwd = getOptionValue("password")
                    createAccountInternal(TERABIT_JKS_FILE_NAME, TERABIT_JKS_FILE_PWD,alias,pwd)
                    exitProcess(0)
                }else{
                    printHelpMessageAndExit("creat_account")
                }
            }
            hasOption("read_account")->{

            }
            hasOption("help")->{
                printHelpMessageAndExit("transfer");
            }
            hasOption("transfer")->{
                if((hasOption("to") && hasOption("from") && hasOption("sign"))){
                    val sign = getOptionValue("sign")
                    val from = getOptionValue("from")
                    val to = getOptionValue("to")
                    val amount = getOptionValue("amount")
                    reqCmd = "transfer?from=$from&to=$to&amount=$amount&sign=&$sign"
                }else{
                    printHelpMessageAndExit("transfer")
                }
            }
            else->{
                printHelpMessageAndExit()
            }
        }
    }


    val httpClient = HttpClient.newHttpClient()
    val reqBuilder = HttpRequest.newBuilder()
        .timeout(Duration.ofSeconds(30))
    val uris = "http://localhost:${PRIMARY_HTTP_PORT}/api/$reqCmd"
    println(uris)
    reqBuilder.uri(URI(uris))
    val resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
    println(resp.body())
}

fun createAccount(alias:String, password:String, account: Account? = null){
    createAccountInternal(TERABIT_JKS_FILE_NAME, TERABIT_JKS_FILE_PWD, alias, password, account)
}
fun createAccountInternal(jksFileName:String, jksFilePwd:String, alias:String, pwd:String, account:Account? = null):Boolean {
//    X509v3CertificateBuilder
    val ks = KeyStore.getInstance("JKS")

    val file = File(jksFileName)
    if(file.exists()){
        ks.load(FileInputStream(file),jksFilePwd.toCharArray())
    }else{
        ks.load(null,null)
    }

    if(ks.containsAlias(alias))
    {
        println("Account alias already exist on this machine, please user another alias.")
        return false
    }
    val uuid = UUID.randomUUID().toString().replace("-","")
    val result = TerabitJavaCodes.createSelfSignedCert(TERABIT_JKS_FILE_NAME, TERABIT_JKS_FILE_PWD,alias,pwd)
    if(result.code != 0){
        println("""Create account key failed, reason is: ${result.desc}""")
        exitProcess(0)
    }

    val entry = result.entry
    val priv = base64Enc(entry.privateKey.encoded)
    val pub =  base64Enc(entry.certificate.publicKey.encoded)

    account?.uuid = uuid
    account?.pswHash = pwd
    account?.pubKey = pub
    account?.priKey = priv

    println("""
        uuid=$uuid
        pwd=$pwd
        pub=$pub
        priv=$priv
    """.trimIndent())
    return true
}

const val allHelp = """
        Terabit cli, ver 0.1
        Supported commands:
        create_account: Create a new account
        query_account:
        query_block:
        transfer: cliKt transfer <from> <to> <amount> <sign>
    """

fun printHelpMessageAndExit(cmd: String = "all") {
    when(cmd){
        "all"-> println(allHelp)
        "transfer"->{
            printHelpMsg(
                "Transfer terabit coin to an account",
                "cli -transfer -from <address> -to <address> -amount <amount> -sign <sign>",
                "cli -transfer -from 7abcdef12c536 -to 7abcdef12c531 -amount 20 -sign 112abcdef12c536"
            )
        }
        "create_account"-> {
            printHelpMsg(
                "Create a terabit account",
                "cli -create_account -name <display name> -password <mypassword>",
                "cli -create_account -name Alice -password 123456"
            )
        }
    }
    exitProcess(0)
}

fun printHelpMsg(title:String, grammar:String, example:String){
    println("""
        Description:$title
        Grammar:$grammar
        Example:$example
    """.trimIndent())
}

