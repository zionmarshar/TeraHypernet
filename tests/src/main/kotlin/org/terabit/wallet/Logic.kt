package org.terabit.wallet

import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.terabit.common.getSha256
import org.terabit.primary.TERABIT_JKS_FILE_PWD
import org.terabit.primary.TerabitJavaCodes
import java.io.*
import java.security.KeyStore
import javax.swing.JOptionPane

object Logic {

    private var username = ""
    private var password = ""
    private var privateKey: ByteArray = EMPTY_BYTE_ARRAY //for the convenience of testing, should not be stored
    private var publicKey: ByteArray = EMPTY_BYTE_ARRAY
    private var addr: ByteArray = EMPTY_BYTE_ARRAY

    val getInfo: ((String)->Any) = { key ->
        when (key) {
            "name" -> username
            "password" -> password
            "priKey" -> privateKey
            "pubKey" -> publicKey
            "address" -> getAddress()
            else -> ""
        }
    }

    private fun getAddress(): ByteArray {
        if (addr.isEmpty()) {
            addr = getSha256(publicKey)
        }
        return addr
    }

    private fun setKey(entry: KeyStore.PrivateKeyEntry) {
        privateKey = entry.privateKey.encoded
        publicKey = entry.certificate.publicKey.encoded

        println("----address=${ByteUtil.toHexString(getAddress())}")
    }

    fun login(name: String?, psw: String?, jksFile: String): Boolean {
        if (name == null || name.isEmpty() || psw == null || psw.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Invalid input")
            return false
        }

        val ks = KeyStore.getInstance("JKS")
        try{
            val file = File(jksFile)
            if(file.exists()){
                ks.load(FileInputStream(file), TERABIT_JKS_FILE_PWD.toCharArray())
            }else{
                ks.load(null,null)
            }
        }catch (e:Exception){
            JOptionPane.showMessageDialog(null, "Open key store failed reason= ${e.localizedMessage}",
                    "Error", JOptionPane.ERROR_MESSAGE)
            return false
        }

        username = name
        password = psw
        if (ks.containsAlias(name)) {
            try{
                val proPass = KeyStore.PasswordProtection(psw.toCharArray())
                val pkEntry = ks.getEntry(name, proPass) as KeyStore.PrivateKeyEntry

                setKey(pkEntry)
                return true
            } catch (e:Exception){
                JOptionPane.showMessageDialog(null, "Username or password error", "Error", JOptionPane.ERROR_MESSAGE)
            }
            return false
        }

        if (JOptionPane.showConfirmDialog(null, "No user, create?", "Notice",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return false
        }

        val result = TerabitJavaCodes.createSelfSignedCert(jksFile, TERABIT_JKS_FILE_PWD, name, psw)
        if(result.code != 0){
            JOptionPane.showMessageDialog(null, "Create account key failed, reason is: ${result.desc}",
                    "Error", JOptionPane.ERROR_MESSAGE)
            return false
        }
        setKey(result.entry)
        return true
    }
}