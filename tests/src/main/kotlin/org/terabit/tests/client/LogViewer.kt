package org.terabit.tests.client

import org.terabit.user.FrameTool
import org.terabit.user.FrameTool.getFrame
import java.awt.Color
import java.util.*
import javax.swing.JButton
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


var dealt = false
val logMap = LinkedHashMap<String, LinkedList<String>>()

fun main(){
    //test Frame
    val frame = getFrame("Terabit log viewer", 1342, 800, 68)

    frame.setLocationRelativeTo(null)

    FrameTool.logPanel?.document?.addDocumentListener(object: DocumentListener {
        override fun changedUpdate(e: DocumentEvent) {
        }

        override fun insertUpdate(e: DocumentEvent) {
            if (dealt || e.length <= 0) {
                return
            }
            dealt = true
            val text = FrameTool.logPanel?.text ?: return
            val lines = text.split('\n')
            for (l in lines) {
                if (l.startsWith("Pri(") || l.startsWith("Min(")) {
                    val first = l.indexOf(' ', 4)
                    if (first < 0) {
                        continue
                    }
                    addLog(l.substring(0, first), l.substring(first + 1))
                    continue
                }
                addLog("Other", l)
            }

            val keys = logMap.keys.sorted()
            for ((i, k) in keys.withIndex()) {
                getButton(k, i)
            }
        }

        override fun removeUpdate(e: DocumentEvent?) {
        }
    })

    frame.isVisible = true
}

private fun changeBtn(btn: JButton) {
    FrameTool.lastBtn?.background = null
    FrameTool.lastBtn = btn
    FrameTool.lastBtn?.background = Color.LIGHT_GRAY
    val list = logMap[FrameTool.lastBtn!!.actionCommand] ?: return
    FrameTool.logPanel?.text = ""
    for (s in list) {
        FrameTool.logPanel?.append(s + "\n")
    }
}

fun getButton(lab: String, index: Int) {
    val lineIndex = index % 21
    val btn = FrameTool.addTopBtn(lab, 16 + 62 * lineIndex, 10 + index / 21 * 30, 60, 25, index) { btn -> changeBtn(btn) }
    btn?.actionCommand = lab
}

private fun addLog(key: String, value: String) {
    var list = logMap[key]
    if (list == null) {
        list = LinkedList()
        logMap[key] = list
    }
    list.add(value)
}