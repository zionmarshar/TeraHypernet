package org.terabit.wallet

import java.text.SimpleDateFormat
import javax.swing.JTextField
import kotlin.concurrent.thread

val DATE_FORMAT = SimpleDateFormat("yyyy.MM.dd HH:mm:ss")

fun autoSetAccountInfo(nonceField: JTextField, balanceField: JTextField) {
    thread {
        val ac = Net.getAccount(Logic.getInfo("address") as ByteArray)
        if (ac == null) {
            nonceField.text = "1"
            balanceField.text = "0"
            return@thread
        }
        nonceField.text = (ac.nonce + 1).toString()
        balanceField.text = ac.balance.toString()
    }
}