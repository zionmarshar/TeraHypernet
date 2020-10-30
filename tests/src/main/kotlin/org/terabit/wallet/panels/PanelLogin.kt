package org.terabit.wallet.panels

import org.terabit.wallet.Logic
import org.terabit.user.FrameTool.addBtnNoLog
import org.terabit.user.FrameTool.addInput
import org.terabit.user.FrameTool.addLabel
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.border.BevelBorder
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class PanelLogin(setLoginOk: (()->Unit)): JPanel() {
    init {
        val box = JPanel()
        box.background = Color.lightGray
        box.border = BorderFactory.createBevelBorder(BevelBorder.RAISED)
        box.layout = null
        box.preferredSize = Dimension(190, 120)
        addLabel(box, "Username:", 5, 5)
        addLabel(box, "Password:", 5, 40)

        val nameField = addInput(box, "terabit", 80, 5)
        val pswField  = addInput(box, "111111", 80, 40)

        val btnAdd = addBtnNoLog(box, "Login", 60, 80, 70, 30) {
            val name = nameField.text
            val psw = pswField.text

            if (!Logic.login(name, psw, "wallet.jks")) {
                return@addBtnNoLog
            }
            setLoginOk()
        }

        this.add(box)


        //test
        this.addAncestorListener(object: AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                val name = nameField.text
                val psw = pswField.text
                if (name == null || name.isEmpty() || psw == null || psw.isEmpty()) {
                    return
                }
                btnAdd.doClick()
            }

            override fun ancestorMoved(event: AncestorEvent?) {
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
            }
        })
    }
}