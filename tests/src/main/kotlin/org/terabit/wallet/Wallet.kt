package org.terabit.wallet

import org.terabit.primary.IsPrimary
import org.terabit.primary.NodeId
import org.terabit.primary.PrimaryCount
import org.terabit.user.FrameTool
import org.terabit.wallet.panels.*
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.BevelBorder
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private lateinit var txFrame: JFrame
private lateinit var container: JPanel
private lateinit var panelWest: JPanel
private lateinit var panelLogin: PanelLogin
private lateinit var panelAccount: PanelAccount
private lateinit var panelBlock: PanelBlock
private lateinit var panelTxSend: PanelTxSend
private lateinit var panelTxSearch: PanelTxSearch
private lateinit var panelActSend: PanelActSend

private var lastBtn: JButton? = null
private var lastPanel: JPanel? = null

fun main(vararg args: String){

    if (args.isNotEmpty() && args[0] == "withServer") {
//        classPath = ManagementFactory.getRuntimeMXBean().classPath
//        testNode0()
        thread {
            NodeId = 0
            IsPrimary = true
            PrimaryCount = 1
            org.terabit.primary.main()
        }
    }

    txFrame = JFrame("Terabit Wallet")
    val winW = 900
    val winH = 600
    txFrame.setLocation(100, 200)
    txFrame.setSize(winW, winH)
    txFrame.isResizable = false
    txFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    txFrame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
            FrameTool.stopAll()
            exitProcess(0)
        }
    })

    panelWest = JPanel()
    panelWest.border = BorderFactory.createBevelBorder(BevelBorder.RAISED)
    panelWest.preferredSize = Dimension(120, 100)
    panelWest.background = Color.darkGray
    panelWest.layout = null
    val y = 5
    val h = 50
    val s = 10
    val bs = arrayOf("Terabiter", "Block", "Send TX", "Tx search", "<html>Send<br>Activation</html>")
    val bts = arrayOfNulls<JButton>(bs.size)
    for ((i,b) in bs.withIndex()) {
        bts[i] = addMenuBtn(panelWest, b, i, y + (h + s) * i, h)
    }
    panelWest.isVisible = false
    txFrame.add(panelWest, BorderLayout.WEST)

    container = JPanel()
    container.layout = BorderLayout()
    txFrame.add(container, BorderLayout.CENTER)

    panelLogin = PanelLogin {
        container.remove(panelLogin)
        panelWest.isVisible = true
        bts[0]?.doClick()
    }
    panelAccount = PanelAccount()
    panelBlock = PanelBlock()
    panelTxSend = PanelTxSend()
    panelTxSearch = PanelTxSearch()
    panelActSend = PanelActSend()

    container.add(panelLogin)

    txFrame.isVisible = true
}

private fun addMenuBtn(pane: Container, lab: String, mnemonic: Int, y: Int, h: Int): JButton {
    val button = JButton(lab)
    button.font = Font("Monospaced", Font.PLAIN, 14)
    button.setBounds(5, y, 110, h)
    button.margin = Insets(0, 0, 0, 0)
    button.mnemonic = mnemonic
    button.addActionListener { e ->
        val btn = e.source as JButton
        if (btn == lastBtn) {
            return@addActionListener
        }
        lastBtn?.isEnabled = true
        lastBtn = btn
        lastBtn?.isEnabled = false
        container.removeAll()
        lastPanel = when (btn.mnemonic) {
            0 -> panelAccount
            1 -> panelBlock
            2 -> panelTxSend
            3 -> panelTxSearch
            4 -> panelActSend
            else -> null
        }
        if (lastPanel != null) container.add(lastPanel)
        container.repaint()
        container.revalidate()
    }
    pane.add(button)
    return button
}
