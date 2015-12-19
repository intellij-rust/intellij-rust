package org.rust.cargo.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.SystemInfo

object PlatformUtil {

    /**
     * Adjusts filename to become canonical executable one (adding 'exe' extension on Windows, for example)
     */
    fun getCanonicalNativeExecutableName(fileName: String): String {
        return if (SystemInfo.isWindows) "$fileName.exe" else fileName
    }

    /**
     * Runs cargo-executable specified with the given path, supplying it with given parameters
     * and attaching to the running process the listener supplied
     *
     * @return process 'output' object (containing `stderr`/`stdout` streams, exit-code, etc.)
     */
    fun runExecutableWith(cargoPath: String, params: List<String>, listener: ProcessListener? = null): ProcessOutput {
        val cmd = GeneralCommandLine()

        cmd.exePath = cargoPath

        cmd.addParameters(*params.toTypedArray())

        val process = cmd.createProcess()
        val handler = CapturingProcessHandler(process)

        listener?.let { handler.addProcessListener(it) }

        return handler.runProcess()
    }
}
