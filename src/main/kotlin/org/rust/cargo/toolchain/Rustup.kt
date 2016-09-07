package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly

private val LOG = Logger.getInstance(Rustup::class.java)

class Rustup(
    private val pathToRustupExecutable: String,
    private val pathToRustcExecutable: String,
    private val projectDirectory: String
) {

    fun downloadStdlib(): VirtualFile? {
        GeneralCommandLine(pathToRustupExecutable)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "add", "rust-src")
            .exec()
            ?: return null

        val sysroot = GeneralCommandLine(pathToRustcExecutable)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .exec()
            ?.stdout?.trim()
            ?: return null

        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(sysroot)?.findFileByRelativePath("lib/rustlib/src/rust/src")
    }

    @TestOnly
    fun activeToolchain(): String? {
        val output = GeneralCommandLine(pathToRustupExecutable)
            .withWorkDirectory(projectDirectory)
            // See https://github.com/rust-lang-nursery/rustup.rs/issues/450
            .withParameters("show")
            .exec()
            ?: return null

        return output.stdoutLines.dropLastWhile { it.isBlank() }.lastOrNull()
    }

    private fun GeneralCommandLine.exec(): ProcessOutput? {
        val process = createProcess()
        val handler = CapturingProcessHandler(process, Charsets.UTF_8, commandLineString)

        val output = handler.runProcess()
        // We use brand new functionality of rustup, which is only available
        // in nightly, so expect it to fail often.
        if (output.exitCode != 0) {
            LOG.warn("Failed to execute `$commandLineString`" +
                "\ncode  : ${output.exitCode}" +
                "\nstdout:\n${output.stdout}" +
                "\nstderr:\n${output.stderr}")
            return null
        }
        return output
    }
}
