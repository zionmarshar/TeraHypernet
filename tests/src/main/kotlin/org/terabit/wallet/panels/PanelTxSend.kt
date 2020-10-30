package org.terabit.wallet.panels

import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.ethereum.util.ByteUtil
import org.terabit.common.GAS_TRANSACTION
import org.terabit.core.Env
import org.terabit.core.createTransaction
import org.terabit.network.ApiCode
import org.terabit.wallet.Logic.getInfo
import org.terabit.user.FrameTool.addBtnNoLog
import org.terabit.user.FrameTool.addInput
import org.terabit.user.FrameTool.addLabelRight
import org.terabit.wallet.Net
import org.terabit.wallet.autoSetAccountInfo
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class PanelTxSend: JPanel() {
    init {
        this.layout = null

        var x = 5
        addLabelRight(this, "To:", x, 5)
        addLabelRight(this, "Amount:", x, 40)
        addLabelRight(this, "GasPrice:", x, 75)
        addLabelRight(this, "GasLimit:", x, 110)
        addLabelRight(this, "Data:", x, 145)
        addLabelRight(this, "Nonce:", x, 180)
        addLabelRight(this, "Balance:", x, 215)
        addLabelRight(this, "Hash:", x, 300, 70, true)
        addLabelRight(this, "Result:", x, 330, 70, true)

        x = 80
        val toField     = addInput(this, "", x, 5, 550)
        val amountField = addInput(this, "", x, 40, 550)
        val priceField  = addInput(this, Env.getExpectGasPrice().toString(), x, 75, 550)
        val limitField  = addInput(this, GAS_TRANSACTION.toString(), x, 110, 550)
        val dataField   = addInput(this, "", x, 145, 550)
        val nonceField  = addInput(this, "", x, 180, 550)
        val balanceField= addInput(this, "", x, 215, 550, false)
        val hashField   = addInput(this, "", x + 5, 300, 550, false)
        val resultField = addInput(this, "", x + 5, 330, 550, false)

        //todo test
        toField.text = "84d89877f0d4041efb6bf91a16f0248f2fd573e6af05c19f96bedb9f882f7882"
        amountField.text = "10000"
        //todo test

        addBtnNoLog(this, "Check", 640, 5, 70, 25) {
            try {
                val to = ByteUtil.hexStringToBytes(toField.text)
                if (to.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "null address input")
                    return@addBtnNoLog
                }
                val ac = Net.getAccount(to)
                if (ac == null) {
                    JOptionPane.showMessageDialog(null, "no address")
                    return@addBtnNoLog
                }
                JOptionPane.showMessageDialog(null, "address balance: ${ac.balance}, nonce: ${ac.nonce}")
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "error")
            }
        }

        val isString = JCheckBox("String data")
        isString.setBounds(635, 145, 98, 25)
        isString.isSelected = true
        this.add(isString)

        addBtnNoLog(this, "Send", 15, 260, 70, 30) {
            it.isEnabled = false
            resultField.text = ""
            hashField.text = ""

            try {
                //test, skip security check
                val to = ByteUtil.hexStringToBytes(toField.text)
                val amount = amountField.text.toLong()
                val price = priceField.text.toLong()
                val limit = limitField.text.toLong()
                val nonce = nonceField.text.toLong()
                val dataStr = dataField.text
                val data = if (dataStr == null || dataStr.isEmpty()) {
                    EMPTY_BYTE_ARRAY
                } else if (isString.isSelected) {
                    dataField.text.toByteArray()
                } else {
                    ByteUtil.hexStringToBytes(dataField.text)
                }

                val tx = createTransaction(nonce, amount, price, limit, getInfo("priKey") as ByteArray,
                        getInfo("pubKey") as ByteArray, to, data)

                val result = Net.sendTx(tx)
                if (result == null) {
                    resultField.text = "Code error"
                    return@addBtnNoLog
                }

                resultField.text = result.optString("desc")
                if (result.optInt("code", ApiCode.SERVER_ERROR.code) != ApiCode.SUCCESS.code) {
                    return@addBtnNoLog
                }

                hashField.text = ByteUtil.toHexString(tx.getHash())
            } catch (e: Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(null, e.toString())
            } finally {
                it.isEnabled = true
            }
        }

        this.addAncestorListener(object: AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                autoSetAccountInfo(nonceField, balanceField)
            }

            override fun ancestorMoved(event: AncestorEvent?) {
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
            }
        })
    }
}