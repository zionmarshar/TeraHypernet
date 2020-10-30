package org.terabit.wallet.panels

import org.terabit.tests.client.*
import org.terabit.user.FrameTool.addLabel
import org.terabit.user.FrameTool.uiFont
import org.terabit.wallet.Net
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import kotlin.concurrent.thread

class PanelBlock: JPanel() {
    private var monitorThread: MyThread? = null
    private var curHeight = 0

    init {
        val topPanel = JPanel()
        topPanel.layout = null
        topPanel.preferredSize = Dimension(0, 40)
        addLabel(topPanel, "Block:", 5, 5)

        val logLabel = JTextArea()
        logLabel.font = uiFont

        drop = addBlockDrop(topPanel, logLabel, 60, 5)
        drop?.isEnabled = true

        btnPrev = addTxBtn(topPanel, "Prev", 100, 160, 5)
        btnNext = addTxBtn(topPanel, "Next", 200, 210, 5)

        val monitor = JCheckBox("Monitor block")
        monitor.setBounds(500, 5, 200, 25)
        monitor.font = Font("Monospaced", Font.PLAIN, 14)
        monitor.isSelected = false
        monitor.addActionListener { onCheckChange(monitor.isSelected) }
        topPanel.add(monitor)

        autoCheck = JCheckBox("Auto refresh")
        autoCheck.isSelected = true

        this.layout = BorderLayout()
        this.add(topPanel, BorderLayout.NORTH)
        this.add(logLabel)
        this.addAncestorListener(object: AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                thread {
                    val url = "http://127.0.0.1:9501/api/block/get_best_height"
                    val height = Net.getJson(url)?.optInt("height", -1) ?: return@thread
                    if (height < 0) {
                        return@thread
                    }
                    curHeight = height
                    updateBlockHeight(height)
                }
            }

            override fun ancestorMoved(event: AncestorEvent?) {
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
                monitor.isSelected = false
                onCheckChange(false)
            }
        })
    }

    private fun onCheckChange(isSelected: Boolean) {
        drop?.isEnabled = !isSelected
        btnPrev.isEnabled = !isSelected
        btnNext.isEnabled = !isSelected
        monitorThread?.stopMe()
        monitorThread = null
        if (isSelected) {
            monitorThread = MyThread()
            monitorThread?.start()
        }
    }

    inner class MyThread: Thread() {
        private var isRun = true

        override fun run() {
            while (isRun) {
                val url = "http://127.0.0.1:9501/api/block/monitor?height=$curHeight"
                val height = Net.getJson(url, true)?.optInt("height", -1) ?: continue
                if (height < 0) {
                    continue
                }
                if (!isRun) {
                    break
                }
                curHeight = height
                updateBlockHeight(height)
            }
        }

        fun stopMe() {
            isRun = false
        }
    }
}