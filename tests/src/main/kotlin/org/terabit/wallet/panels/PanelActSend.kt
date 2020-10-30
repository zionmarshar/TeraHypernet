package org.terabit.wallet.panels

import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.ethereum.util.ByteUtil
import org.terabit.common.CONTRACT_METHOD_APPLY_FOR_ACTIVATION
import org.terabit.common.CONTRACT_VERSION_INIT
import org.terabit.core.Env.getMethod1Gas
import org.terabit.core.createTransaction
import org.terabit.network.ApiCode
import org.terabit.wallet.DATE_FORMAT
import org.terabit.wallet.Logic.getInfo
import org.terabit.user.FrameTool.uiFont
import org.terabit.user.FrameTool.addBtnNoLog
import org.terabit.user.FrameTool.addInput
import org.terabit.user.FrameTool.addLabelRight
import org.terabit.wallet.Net
import org.terabit.wallet.Net.getActivation
import org.terabit.wallet.autoSetAccountInfo
import java.awt.Insets
import java.util.*
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener


class PanelActSend: JPanel() {
    init {
        this.layout = null

        var x = 5
        addLabelRight(this, "GasPrice:", x, 5)
        addLabelRight(this, "GasLimit:", x, 40)
        addLabelRight(this, "Nonce:", x, 75)
        addLabelRight(this, "Balance:", x, 110)
        addLabelRight(this, "Hash:", x, 200, 70, true)
        addLabelRight(this, "Result:", x, 230, 70, true)

        x = 80
        val priceField  = addInput(this, "4", x, 5, 550)
        val limitField  = addInput(this, getMethod1Gas().toString(), x, 40, 550)
        val nonceField  = addInput(this, "", x, 75, 550)
        val balanceField= addInput(this, "", x, 110, 550, false)

        val hashField   = addInput(this, "", x + 5, 200, 550, false)
        val resultField = addInput(this, "", x + 5, 230, 550, false)

        addBtnNoLog(this, "Send", 15, 160, 70, 30) {
            it.isEnabled = false
            resultField.text = ""
            hashField.text = ""

            try {
                //test, skip security check
                val price = priceField.text.toLong()
                val limit = limitField.text.toLong()
                val nonce = nonceField.text.toLong()
                val data = ByteUtil.merge(ByteUtil.shortToBytes(CONTRACT_VERSION_INIT),
                        ByteUtil.shortToBytes(CONTRACT_METHOD_APPLY_FOR_ACTIVATION))

                val tx = createTransaction(nonce, 0L, price, limit, getInfo("priKey") as ByteArray,
                        getInfo("pubKey") as ByteArray, EMPTY_BYTE_ARRAY, data)
                println("--------------send activation: tx=$tx")

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

        /************************************* check status *********************************/
        val info = JTextArea()
        info.setBounds(7, 315, 600, 110)
        info.font = uiFont
        info.margin = Insets(2, 5, 5, 5)
        this.add(info)

        val addrField = addInput(this, "Address to check, default is me", 120, 283, 550)

        addBtnNoLog(this, "Check status", 5, 280, 110, 30) {
            try {
                val text = addrField.text
                var address: ByteArray? = null
                try {
                    if (text != null && text.isNotEmpty()) {
                        address = ByteUtil.hexStringToBytes(text)
                    }
                } catch (e: Exception) {
                }
                if (address == null) {
                    address = getInfo("address") as ByteArray
                }

                val act = getActivation(address)
                if (act == null) {
                    info.text = "not found"
                    return@addBtnNoLog
                }
                val end = if (act.timeEnd == 0L) "long-time" else DATE_FORMAT.format(Date(act.timeEnd * 1000L))
                info.text = "address:     ${ByteUtil.toHexString(address)}\n" +
                            "timeStart:   ${DATE_FORMAT.format(Date(act.timeStart * 1000L))}\n" +
                            "timeEnd:     $end\n" +
                            "blockHeight: ${act.blockHeight}\n" +
                            "txIndex:     ${act.txIndex}\n" +
                            "flag:        ${act.flag}"
            } catch (e: Exception) {
                info.text = "error"
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