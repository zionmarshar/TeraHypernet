package org.terabit.tests.client

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ethereum.util.ByteUtil
import org.ethereum.util.RLP
import org.json.JSONArray
import org.json.JSONObject
import org.terabit.common.base64Dec
import org.terabit.common.base64Enc
import org.terabit.common.sleep
import org.terabit.core.Env
import org.terabit.core.FinalBlock
import org.terabit.core.Transaction
import org.terabit.core.TxCode
import org.terabit.primary.IsPrimary
import org.terabit.primary.NodeId
import org.terabit.primary.PrimaryCount
import org.terabit.tests.testAddr
import org.terabit.tests.testPriKey
import org.terabit.tests.testPubKey
import org.terabit.user.FrameTool
import org.terabit.user.FrameTool.BLOCK_LOG_INDEX
import org.terabit.user.FrameTool.addLabel
import org.terabit.user.FrameTool.addTopBtn
import org.terabit.user.FrameTool.getFrame
import org.terabit.user.FrameTool.startCmd
import org.terabit.user.FrameTool.stop
import org.terabit.user.FrameTool.stopAll
import org.terabit.user.FrameTool.uiFont
import org.terabit.wallet.DATE_FORMAT
import java.awt.Color
import java.awt.Container
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.io.File
import java.lang.management.ManagementFactory
import java.util.*
import javax.swing.*
import javax.swing.border.BevelBorder
import kotlin.concurrent.thread
import kotlin.system.exitProcess

const val PRIMARY_NODE_COUNT = 4
const val MINOR_NODE_COUNT = 0

var classPath = ""

private var only0 = false
private var only0Process: Process? = null

fun main(vararg args: String){

    File("./dbs/").mkdir()
    classPath = ManagementFactory.getRuntimeMXBean().classPath

    if (args.isNotEmpty() && args[0] == "only0") {
        only0 = true
        startTxFrame()
        testNode0()
        return
    }

    startTxFrame()
    startMainFrame()

    //primary node
    val firstIds = IntRange(0, PRIMARY_NODE_COUNT - 1)
    startNode(firstIds, true)

    //minor node
    val secondIds = IntRange(11, 11 + MINOR_NODE_COUNT - 1)
    startNode(secondIds, false)
}

fun testNode0() {
    NodeId = 0
    IsPrimary = true
    PrimaryCount = 1
    org.terabit.primary.main()
}

private fun startMainFrame() {
    val frame = getFrame("Terabit test", 1342, 800, 68)
    frame.setLocation(365, 0)

    val btnBlock = addActionBtn("Block", 940, BLOCK_LOG_INDEX)
    addActionBtn("Detail", 1020, -2000, true)
    addActionBtn("Start0", 1100, -3000, true)
    addActionBtn("Stop1", 1180, -5000, true)
    addActionBtn("Stop", 1260, -4000, true)

    btnBlock.doClick()
}

fun addButton(lab: String, index: Int) {
    val lineIndex = index % 21
    addTopBtn(lab, 16 + 62 * lineIndex, 10 + index / 21 * 30, 60, 25, index) { btn -> changeBtn(btn) }
}

private fun addActionBtn(lab: String, x: Int, mnemonic: Int, noLog: Boolean = false): JButton {
    val btn = addActionBtn(lab, x, 40, mnemonic)
    if (noLog) {
        btn.actionCommand = "noLog"
    }
    return btn
}

fun addActionBtn(lab: String, x: Int, y: Int, mnemonic: Int): JButton {
    return FrameTool.addActionBtn(lab, x, y, mnemonic) { btn -> changeBtn(btn)}
}

private fun startNode(ids: IntRange, isFirst: Boolean) {
    val lab = if (isFirst) "Pri" else "Min"
    for (i in ids) {
        val index = if (isFirst) i else (i - 11 + PRIMARY_NODE_COUNT)
        addButton("$lab($i)", index)

        val cmd = "java -classpath $classPath org.terabit.tests.client.PrimaryNodeKt $i $isFirst $PRIMARY_NODE_COUNT"
        startCmd(cmd, lab, i) { info ->
            try {
                val index1 = info.indexOf("height:[")
                if (index1 < 0) {
                    return@startCmd
                }
                val index2 = info.indexOf(']', index1)
                val height = info.substring(index1 + 8, index2).toInt()
                updateBlockHeight(height)
            } catch (e: Exception) {
            }
        }
    }
}

private fun changeBtn(newBtn: JButton) {
    when (newBtn.mnemonic) {
        -2000 -> {
            if (txFrame.isVisible) {
                return
            }
            txFrame.isVisible = true
        }
        -3000 -> {
            if (FrameTool.hasProcess()) {
                return
            }
            thread {
                testNode0()
            }
            newBtn.isEnabled = false
        }
        -4000 -> {
            newBtn.isEnabled = false
            stopAll()
        }
        -5000 -> {
            newBtn.isEnabled = false
            stop(1)
        }
    }
}


private lateinit var txFrame: JFrame
private val model = UserDefineComboBoxModel()
private lateinit var logLabel: JTextArea
private val balanceLabs = arrayOfNulls<JLabel?>(5)
var drop: JComboBox<Int>? = null
lateinit var btnPrev: JButton
lateinit var btnNext: JButton
lateinit var autoCheck: JCheckBox
private fun startTxFrame() {
    val winW = 900
    val winH = 600
    txFrame = getFrame("Terabit detail", winW, winH, 0) {
        if (only0) {
            stopAll()
            only0Process?.toHandle()?.destroyForcibly()
            sleep(200)
            exitProcess(0)
        }
    }
    if (!only0) {
        txFrame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
    }
    txFrame.setLocation(100, 200)
    txFrame.isResizable = false
    txFrame.layout = null

    val pane = txFrame.contentPane

    addLabel(pane, "0[${testAddr[0].substring(0, 8)}]:", 0)
    addLabel(pane, "1[${testAddr[1].substring(0, 8)}]:", 50)
    addLabel(pane, "2[${testAddr[2].substring(0, 8)}]:", 100)
    addLabel(pane, "3[${testAddr[3].substring(0, 8)}]:", 150)
    addLabel(pane, "T[${testAddr[4].substring(0, 8)}]:", 200)
    balanceLabs[0] = addLabel(pane, "0", 15, true)
    balanceLabs[1] = addLabel(pane, "0", 65, true)
    balanceLabs[2] = addLabel(pane, "0", 115, true)
    balanceLabs[3] = addLabel(pane, "0", 165, true)
    balanceLabs[4] = addLabel(pane, "0", 215, true)

    logLabel = JTextArea()
    logLabel.font = uiFont

    addLabel(pane, "Block:", 250)
    drop = addBlockDrop(pane, logLabel, 5, 270)

    btnPrev = addTxBtn(pane, "Prev", 100, 5)
    btnNext = addTxBtn(pane, "Next", 200, 52)
    btnPrev.isEnabled = false
    btnNext.isEnabled = false

    //auto refresh
    autoCheck = JCheckBox("Auto refresh")
    autoCheck.setBounds(1, 330, 98, 25)
    autoCheck.isSelected = true
    autoCheck.addActionListener {
        drop?.isEnabled = !autoCheck.isSelected
        btnPrev.isEnabled = !autoCheck.isSelected
        btnNext.isEnabled = !autoCheck.isSelected
    }
    pane.add(autoCheck)


    addTxBtn(pane, "SendTx", 300, 8, 380, 60)

    val pane1 = JScrollPane()
    pane1.setBounds(100, 5, winW - 117, winH - 46)
    pane1.setViewportView(logLabel)
    pane1.background = Color.LIGHT_GRAY
    pane1.isOpaque = true
    pane1.border = BorderFactory.createBevelBorder(BevelBorder.RAISED)
    pane.add(pane1)

    txFrame.isVisible = true

    thread {
        sleep(200)
        (pane as JPanel).updateUI()
    }
}

fun addBlockDrop(pane: Container, log: JTextArea, x: Int, y: Int): JComboBox<Int> {
    val drop = JComboBox(model)
    drop.setBounds(x, y, 90, 25)
    drop.isEnabled = false
    drop.addItemListener { e ->
        if (e.stateChange == ItemEvent.DESELECTED) {
            return@addItemListener
        }
        log.text = ""
        for (lab in balanceLabs) {
            lab?.text = "0"
        }
        GlobalScope.launch {
            val height = e.item as Int
            val block = getBlock(height)
            if (block == null) {
                updateHeight = -1
                return@launch
            }
            log.append("[height] = $height\n")
            log.append("[parent] = ${ByteUtil.toHexString(block.header.parentHash)}\n")
            log.append("[hash  ] = ${ByteUtil.toHexString(block.getHash())}\n")
            log.append("[miner ] = ${ByteUtil.toHexString(block.header.miner)}\n")
            log.append("[state ] = ${ByteUtil.toHexString(block.header.stateRoot)}\n")
            log.append("[txRoot] = ${ByteUtil.toHexString(block.header.txRoot)}\n")
            log.append("[time  ] = ${DATE_FORMAT.format(Date(block.header.timestamp * 1000L))}\n")
            log.append("[diff  ] = ${block.header.difficulty}\n")
            log.append("[round ] = ${block.header.minorRound}\n")
            log.append("[extra ] = ${block.header.getExtraData()}\n")
            log.append("[reward] = ${Env.getMinerShouldReward(block.getHeight())}\n")
            log.append("[limit ] = ${block.header.gasLimit}\n")
            log.append("[used  ] = ${block.header.gasUsed}\n")
            log.append("[fees  ] = ${block.header.gasCost}\n\n")
            log.append("[NEXT_L] = ${block.header.nextGasLimit()}\n")
            log.append("\n")

            //Transaction
            val txResult = getTxResult(height)
            val txCode = txResult?.optJSONArray("result")
            log.append("***************************************Transaction*********************************************\n")
            for ((i, tx) in block.txList.withIndex()) {
                println("----tx hash=${ByteUtil.toHexString(tx.getHash())}")
                log.append(tx.toString())
                if (txCode != null && i < txCode.length()) {
                    log.append("   ${if (txCode.getInt(i) == TxCode.SUCCESS.code) "SUCCESS" else "FAILED"}")
                }
                log.append("\n")
            }

            //balance
            val balance = getBalance(height)
            updateHeight = -1
            if (balance == null || balance.length() < 4) {
                return@launch
            }
            for ((i, lab) in balanceLabs.withIndex()) {
                lab?.text = balance.getLong(i).toString()
            }
        }
    }
    pane.add(drop)
    return drop
}

private fun addLabel(pane: Container, name: String, y: Int, blue: Boolean = false): JLabel {
    return addLabel(pane, name, 5, y, 100, blue)
}

fun addTxBtn(pane: Container, lab: String, mnemonic: Int, x: Int, y: Int = 300, w: Int = 45): JButton {
    val btn = JButton(lab)
    btn.font = Font("Monospaced", Font.PLAIN, 12)
    btn.setBounds(x, y, w, 25)
    btn.margin = Insets(0, 0, 0, 0)
    btn.mnemonic = mnemonic
    btn.addActionListener { e -> doTxBtn(e) }
    pane.add(btn)
    return btn
}

private fun doTxBtn(e: ActionEvent) {
    if (drop == null) {
        return
    }
    val btn = e.source as JButton
    when (btn.mnemonic) {
        100 -> { drop!!.selectedIndex = drop!!.selectedIndex - 1 }
        200 -> { drop!!.selectedIndex = drop!!.selectedIndex + 1 }
        300 -> { sendTx() }
    }
    drop!!.updateUI()
}

var updateHeight = -1
private var lastHeight = -1
fun updateBlockHeight(height: Int) {
    try {
        lastHeight = height
        thread {
            sleep(800)
            if (height == lastHeight && lastHeight != updateHeight) {
                changeSelect(lastHeight)
            }
        }
        if (updateHeight != -1) {
            return
        }
        updateHeight = height
        if (autoCheck.isSelected) {
            thread {
                changeSelect(height)
            }
        }
    } catch (e: Exception) {
    }
}

private fun changeSelect(height: Int) {
    model.max = height
    sleep(200)
    drop?.selectedIndex = model.max
    sleep(100)
    drop?.updateUI()
}

private val client = OkHttpClient()
private fun getBlock(height: Int): FinalBlock? {
    try {
        val request = Request.Builder().url("http://127.0.0.1:9501/api/block/get_by_height?height=$height").build()
        //println("----url: ${request.url}")
        val response = client.newCall(request).execute()
        //println("----response: $response")
        if (!response.isSuccessful) {
            return null
        }
        val json = response.body?.string() ?: return null
        //println("----json: $json")
        val data = JSONObject(json).optString("data") ?: return null
        val bytes = base64Dec(data)
        return FinalBlock(RLP.decodeList(bytes))
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun getTxResult(height: Int): JSONObject? {
    try {
        val request = Request.Builder().url("http://127.0.0.1:9501/api/tx/list_result_by_height?height=$height").build()
        //println("----tx url: ${request.url}")
        val response = client.newCall(request).execute()
        //println("----tx response: $response")
        if (!response.isSuccessful) {
            return null
        }
        val json = response.body?.string() ?: return null
        println("----tx json: $json")
        return JSONObject(json)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun getBalance(height: Int): JSONArray? {
    try {
        val param = JSONObject()
        param.put("height", height)
        param.put("addr", JSONArray(testAddr))
        val body = param.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url("http://127.0.0.1:9501/api/account/list_some_by_height").post(body).build()
//        println("----balance url: ${request.url}")
        val response = client.newCall(request).execute()
//        println("----balance response: $response")
        if (!response.isSuccessful) {
            return null
        }
        val json = response.body?.string() ?: return null
        println("----balance json: $json")
        return JSONObject(json).optJSONArray("balance")
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun sendTx(): JSONObject? {
    try {
        val tran = Transaction(4, 100, 1, 20000,
                ByteUtil.hexStringToBytes(testPubKey[0]),
                ByteUtil.hexStringToBytes(testAddr[4]))
        tran.sign(ByteUtil.hexStringToBytes(testPriKey[0]))

        val body = base64Enc(tran.encode()).toRequestBody()
        val request = Request.Builder().url("http://127.0.0.1:9501/api/tx/send").post(body).build()
        client.newCall(request).execute()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

internal class UserDefineComboBoxModel : AbstractListModel<Int>(), ComboBoxModel<Int> {
    var max = -1
    private var item: Int? = null

    override fun getElementAt(index: Int): Int {
        if (index < 0 || index > max) {
            return -1
        }
        return index
    }

    override fun getSize(): Int {
        return max + 1
    }

    override fun setSelectedItem(anItem: Any) {
        item = anItem as Int
    }

    override fun getSelectedItem(): Any? {
        return item
    }
}
