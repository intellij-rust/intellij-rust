package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.rust.utils.fullyRefreshDirectory
import org.rust.utils.seconds

private val LOG = Logger.getInstance(Rustup::class.java)

class Rustup(
    private val pathToRustupExecutable: String,
    private val pathToRustcExecutable: String,
    private val projectDirectory: String
) {
    sealed class DownloadResult {
        class Ok(val library: VirtualFile) : DownloadResult()
        class Err(val error: String) : DownloadResult()
    }

    fun downloadStdlib(): DownloadResult {
        val downloadProcessOutput = GeneralCommandLine(pathToRustupExecutable)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "add", "rust-src")
            .exec()

        return if (downloadProcessOutput.exitCode != 0) {
            DownloadResult.Err("rustup failed: `${downloadProcessOutput.stderr}`")
        } else {
            val sources = getStdlibFromSysroot() ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
            fullyRefreshDirectory(sources)
            DownloadResult.Ok(sources)
        }
    }

    fun getStdlibFromSysroot(): VirtualFile? {
        val sysroot = GeneralCommandLine(pathToRustcExecutable)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .exec(5.seconds)
            .stdout.trim()


        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }

    private fun GeneralCommandLine.exec(timeoutInMilliseconds: Int? = null): ProcessOutput {
        val handler = CapturingProcessHandler(this)

        LOG.info("Executing `$commandLineString`")
        val output = if (timeoutInMilliseconds != null)
            handler.runProcess(timeoutInMilliseconds)
        else
            handler.runProcess()

        if (output.exitCode != 0) {
            LOG.warn("Failed to execute `$commandLineString`" +
                "\ncode  : ${output.exitCode}" +
                "\nstdout:\n${output.stdout}" +
                "\nstderr:\n${output.stderr}")
        }

        return output
    }
}
