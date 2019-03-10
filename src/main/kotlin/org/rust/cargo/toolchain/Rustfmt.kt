/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil.writeInRunUndoTransparentAction
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.nio.file.Path

class Rustfmt(private val rustfmtExecutable: Path) {

    @Throws(ExecutionException::class)
    fun reformatFile(
        cargoProject: CargoProject,
        file: VirtualFile,
        owner: Disposable = cargoProject.project,
        stdIn: ByteArray? = null,
        overwriteFile: Boolean = true,
        skipChildren: Boolean = checkSupportForSkipChildrenFlag(cargoProject)
    ): ProcessOutput? {
        if (file.isNotRustFile || !file.isValid) return null
        val arguments = buildList<String> {
            add("--emit=${if (overwriteFile) "files" else "stdout"}")
            if (skipChildren) {
                add("--unstable-features")
                add("--skip-children")
            }
            if (stdIn == null) add(file.path)
        }

        return try {
            GeneralCommandLine(rustfmtExecutable)
                .withWorkDirectory(cargoProject.workingDirectory)
                .withParameters(arguments)
                .execute(owner, false, stdIn = stdIn)
        } catch (e: ExecutionException) {
            if (isUnitTestMode) throw e else null
        }
    }

    @Throws(ExecutionException::class)
    fun reformatDocument(
        cargoProject: CargoProject,
        document: Document,
        owner: Disposable = cargoProject.project,
        skipChildren: Boolean = checkSupportForSkipChildrenFlag(cargoProject)
    ) {
        if (!document.isWritable) return
        val processOutput = reformatFile(
            cargoProject = cargoProject,
            file = document.virtualFile ?: return,
            owner = owner,
            stdIn = document.text.toByteArray(),
            overwriteFile = false,
            skipChildren = skipChildren
        ) ?: return
        writeInRunUndoTransparentAction { document.setText(processOutput.stdout) }
    }

    fun checkSupportForSkipChildrenFlag(cargoProject: CargoProject): Boolean {
        if (!cargoProject.project.rustSettings.useSkipChildren) return false
        val channel = cargoProject.rustcInfo?.version?.channel
        if (channel != RustChannel.NIGHTLY) return false
        return GeneralCommandLine(rustfmtExecutable)
            .withParameters("-h")
            .withWorkDirectory(cargoProject.workingDirectory)
            .execute()
            ?.stdoutLines
            ?.contains(" --skip-children ")
            ?: false
    }
}
