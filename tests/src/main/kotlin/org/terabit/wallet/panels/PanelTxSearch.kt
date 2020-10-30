package org.terabit.wallet.panels

import org.ethereum.util.ByteUtil
import org.terabit.core.Transaction
import org.terabit.user.FrameTool.addInput
import org.terabit.user.FrameTool.addLabel
import org.terabit.user.FrameTool.uiFont
import org.terabit.wallet.Net
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.concurrent.thread

class PanelTxSearch: JPanel() {
    init {
        val topPanel = JPanel()
        topPanel.layout = null
        topPanel.preferredSize = Dimension(0, 40)
        addLabel(topPanel, "Tx hash:", 5, 7)
        val hashField = addInput(topPanel, "", 65, 8, 550)

        val logLabel = JTextArea()
        logLabel.font = uiFont

        val btn = JButton("Search")
        topPanel.add(btn)
        btn.font = uiFont
        btn.setBounds(620, 8, 80, 25)
        btn.margin = Insets(0, 0, 0, 0)
        btn.addActionListener {
            val hash = hashField.text
            if (hash == null) {
                logLabel.text = "no hash"
                return@addActionListener
            }

            thread {
                val url = "http://127.0.0.1:9501/api/tx/get_by_hash?hash=$hash"
                val tx = Net.getTerabitData(url, Transaction::class.java)
                if (tx == null) {
                    logLabel.text = "not found"
                    return@thread
                }
                val sb = StringBuilder()
                sb.append("[hash    ]= ${ByteUtil.toHexString(tx.getHash())}\n")
                sb.append("[from    ]= ${ByteUtil.toHexString(tx.getFrom())}\n")
                sb.append("[to      ]= ${ByteUtil.toHexString(tx.to)}\n")
                sb.append("[amount  ]= ${tx.amount}\n")
                sb.append("[nonce   ]= ${tx.nonce}\n")
                sb.append("[gasPrice]= ${tx.gasPrice}\n")
                sb.append("[gasLimit]= ${tx.gasLimit}\n")
                sb.append("[data    ]= ${ByteUtil.toHexString(tx.data)}")

                logLabel.text = sb.toString()
            }
        }

        this.layout = BorderLayout()
        this.add(topPanel, BorderLayout.NORTH)
        this.add(logLabel)
    }
}