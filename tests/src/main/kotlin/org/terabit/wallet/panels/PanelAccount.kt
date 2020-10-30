package org.terabit.wallet.panels

import org.ethereum.util.ByteUtil
import org.terabit.wallet.Logic.getInfo
import org.terabit.user.FrameTool.addInput
import org.terabit.user.FrameTool.addLabelRight
import org.terabit.wallet.autoSetAccountInfo
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class PanelAccount: JPanel() {
    init {
        this.layout = null
        var x = 5
        var w = 85
        addLabelRight(this, "Username:", x, 5, w)
        addLabelRight(this, "Address:", x, 40, w)
        addLabelRight(this, "Balance:", x, 75, w)
        addLabelRight(this, "Next nonce:", x, 110, w)

        x = 95
        w = 600
        val name = addInput(this, "", x, 5, w, false)
        val addr = addInput(this, "", x, 40, w, false)
        val balance = addInput(this, "", x, 75, w, false)
        val nonce = addInput(this, "", x, 110, w, false)

        this.addAncestorListener(object: AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                val address = getInfo("address") as ByteArray

                name.text = getInfo("name") as String
                addr.text = ByteUtil.toHexString(address)

                autoSetAccountInfo(nonce, balance)
            }

            override fun ancestorMoved(event: AncestorEvent?) {
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
            }
        })
    }
}