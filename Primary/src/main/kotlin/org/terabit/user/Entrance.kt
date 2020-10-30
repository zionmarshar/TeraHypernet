package org.terabit.user

import org.terabit.user.FrameTool.BLOCK_LOG_INDEX
import org.terabit.user.FrameTool.startCmd
import org.terabit.user.FrameTool.addActionBtn
import org.terabit.user.FrameTool.container
import org.terabit.user.FrameTool.getFrame
import java.io.File
import java.lang.management.ManagementFactory

fun main(){
    try {
        startFrame()
    } catch (e: Exception) {
    }

    File("./dbs/").mkdir()
    val classPath = ManagementFactory.getRuntimeMXBean().classPath

    val cmd = "java -classpath $classPath org.terabit.primary.MainKt"
    startCmd(cmd, "Primary", 0)
}

private fun startFrame() {
    getFrame("Terabit Primary", 1342, 800, 40)

    val btnBlock = addActionBtn("Block", 16, 10, BLOCK_LOG_INDEX) {
        FrameTool.showBlockLog()
    }
    addActionBtn("Log", 100, 10, 0) {
        FrameTool.showLog(0)
    }
    container?.repaint()

    btnBlock.doClick()
}
