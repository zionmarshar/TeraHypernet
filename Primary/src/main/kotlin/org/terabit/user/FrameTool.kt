package org.terabit.user

import org.terabit.common.curTime
import org.terabit.common.sleep
import org.terabit.core.showBlockHeader
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.util.*
import javax.swing.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object FrameTool: JPanel() {
    val uiFont = Font("Monospaced", Font.PLAIN, 12)
    private val boldFont = Font("Monospaced", Font.BOLD, 14)

    var container: Container? = null
    var logPanel: JTextArea? = null

    fun getFrame(name: String, w: Int, h: Int, topHeight: Int, onClose: (()->Unit)? = null): JFrame {
        val frame = JFrame(name)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(w, h)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                if (onClose != null) {
                    onClose()
                    return
                }
                stopAll()
                sleep(200)
                exitProcess(0)
            }
        })

        container = if (topHeight > 0) {
            val panelNorth = JPanel()
            panelNorth.preferredSize = Dimension(0, topHeight)
            panelNorth.layout = null
            frame.add(panelNorth, BorderLayout.NORTH)

            logPanel = JTextArea()
            logPanel?.font = uiFont

            val pane1 = JScrollPane()
            pane1.setViewportView(logPanel)
            frame.add(pane1)

            panelNorth
        } else {
            frame.contentPane
        }

        frame.isVisible = true

        return frame
    }

    fun addLabel(pane: Container, name: String, x: Int, y: Int, w: Int = 70, blue: Boolean = false): JLabel {
        val lab = JLabel(name)
        lab.setBounds(x, y, w, 25)
        lab.font = uiFont
        if (blue) {
            lab.foreground = Color.BLUE
        }
        pane.add(lab)
        return lab
    }

    fun addLabelRight(pane: Container, name: String, x: Int, y: Int, w: Int = 70, blue: Boolean = false): JLabel {
        val lab = addLabel(pane, name, x, y, w, blue)
        lab.horizontalAlignment = JLabel.RIGHT
        return lab
    }

    fun addInput(pane: Container, text: String, x: Int, y: Int, w: Int = 100, editable: Boolean = true): JTextField {
        val input = JTextField(text)
        input.setBounds(x, y, w, 25)
        input.font = uiFont
        input.isEditable = editable
        pane.add(input)
        return input
    }

    fun addBtnNoLog(pane: Container, lab: String, x: Int, y: Int, width: Int, height: Int,
               onClick: ((JButton)->Unit)): JButton {
        val btn = addBtn(pane, lab, x, y, width, height, 0, onClick)
        btn.actionCommand = "noLog"
        return btn
    }

    fun addBtn(pane: Container, lab: String, x: Int, y: Int, width: Int, height: Int, mnemonic: Int,
               onClick: ((JButton)->Unit)): JButton {
        val btn = JButton(lab)
        btn.font = uiFont
        btn.setBounds(x, y, width, height)
        btn.mnemonic = mnemonic
        btn.margin = Insets(0, 0, 0, 0)
        btn.addActionListener { e -> doClick(e.source as JButton, onClick) }
        pane.add(btn)
        return btn
    }

    fun addActionBtn(lab: String, x: Int, y: Int, mnemonic: Int, onClick: (JButton)->Unit): JButton {
        val btn = JButton(lab)
        btn.font = boldFont
        btn.foreground = Color.blue
        btn.setBounds(x, y, 65, 25)
        btn.margin = Insets(0, 0, 0, 0)
        btn.mnemonic = mnemonic
        btn.addActionListener { e -> doClick(e.source as JButton, onClick) }
        container?.add(btn)
        return btn
    }

    fun addTopBtn(lab: String, x: Int, y: Int, width: Int, height: Int, mnemonic: Int,
                  doClick: ((JButton)->Unit)): JButton? {
        val c = container ?: return null
        val btn = addBtn(c, lab, x, y, width, height, mnemonic, doClick)
        c.repaint()
        return btn
    }

    private fun doClick(btn: JButton, doClick: ((JButton)->Unit)) {
        doClick(btn)

        if (btn.actionCommand == "noLog") {
            return
        }

        lastBtn?.background = null
        lastBtn = btn
        lastBtn?.background = Color.LIGHT_GRAY
        val i = lastBtn!!.mnemonic
        if (i == BLOCK_LOG_INDEX) {
            showBlockLog()
            return
        }
        showLog(i)
    }

    private fun showLog(list: LinkedList<String>) {
        logPanel?.text = ""
        val sb = StringBuilder()
        for (s in list) {
            sb.append(s + "\n")
        }
        logPanel?.append(sb.toString())
    }

    fun showBlockLog() {
        showLog(blockLog)
    }

    fun showLog(index: Int) {
        if (index < 0 || index >= logList.size) {
            return
        }
        showLog(logList[index])
    }

    const val BLOCK_LOG_INDEX = -1000
    private val proList = LinkedList<Process>()
    private val logList = ArrayList<LinkedList<String>>()
    private val blockLog = LinkedList<String>()

    var lastBtn: JButton? = null

    fun hasProcess(): Boolean {
        return proList.size > 0 && proList[0].isAlive
    }

    fun stopAll() {
        for (p in proList) {
            if (p.isAlive) p.toHandle()?.destroyForcibly()
        }
        println("All process is stopped")
    }

    fun stop(index: Int) {
        if (index < 0 || index >= proList.size) {
            return
        }
        thread {
            proList[index].toHandle()?.destroyForcibly()
        }
    }

    fun startCmd(cmd: String, lab: String, i: Int, onBlock: ((String)->Unit)? = null) {
        val p = Runtime.getRuntime().exec(cmd)
        proList.add(p)
        val log = LinkedList<String>()
        logList.add(log)
        thread {
            val br = BufferedReader(InputStreamReader(BufferedInputStream(p.inputStream)))
            var s: String?
            while (p.isAlive) {
                s = br.readLine() ?: break
                println("$lab($i) info: $s")

                if (s.startsWith(showBlockHeader)) {
                    addLog("${getCurTime()} ${s.substring(14)}", blockLog, BLOCK_LOG_INDEX)
                    if (onBlock != null) {
                        onBlock(s)
                    }
                    continue
                }
                if (s.contains("Exception")) {
                    addLog("Exception at $lab($i) $s", blockLog, BLOCK_LOG_INDEX)
                }
                addLog("${getCurTime()} info: $s", log, i)
            }
        }
        thread {
            val br = BufferedReader(InputStreamReader(BufferedInputStream(p.errorStream)))
            var e: String?
            while (p.isAlive) {
                e = br.readLine() ?: break
                System.err.println("$lab($i) error: $e")

                if (e.contains("Exception")) {
                    addLog("Exception at $lab($i) $e", blockLog, BLOCK_LOG_INDEX)
                }
                addLog("${getCurTime()} ERROR: $e", log, i)
            }
        }
    }

    private fun getCurTime(hasMs: Boolean = false): String {
        val cal = Calendar.getInstance()
        if (hasMs) {
            return String.format("[%02d:%02d:%02d.%03d]", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                    cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND))
        }
        return String.format("[%02d:%02d:%02d]", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND))
    }

    private fun addLog(msg: String, log: LinkedList<String>, index: Int) {
        log.add(msg)
        if (log.size > 10000) {
            log.removeFirst()
        }
        if (index == lastBtn?.mnemonic) {
            logPanel?.append(msg + "\n")
            logPanel?.caretPosition = logPanel!!.text.length
        }
    }
}