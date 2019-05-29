/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil.writeInRunUndoTransparentAction
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.nio.file.Path

class Rustfmt(private val rustfmtExecutable: Path) {

    @Throws(ExecutionException::class)
    fun reformatDocument(
        cargoProject: CargoProject,
        document: Document,
        owner: Disposable = cargoProject.project,
        skipChildren: Boolean = checkSupportForSkipChildrenFlag(cargoProject)
    ) {
        checkIsDispatchThread()

        if (!document.isWritable) return
        val file = document.virtualFile ?: return
        if (file.isNotRustFile || !file.isValid) return

        val arguments = buildList<String> {
            add("--emit=stdout")

            if (skipChildren) {
                add("--unstable-features")
                add("--skip-children")
            }

            val path = findConfigPathRecursively(file.parent, stopAt = cargoProject.workingDirectory)
            if (path != null) {
                add("--config-path")
                add(path.toString())
            }
        }

        val processOutput = try {
            cargoProject.project.computeWithCancelableProgress("Reformatting File with Rustfmt...") {
                GeneralCommandLine(rustfmtExecutable)
                    .withWorkDirectory(cargoProject.workingDirectory)
                    .withParameters(arguments)
                    .withCharset(Charsets.UTF_8)
                    .execute(owner, false, stdIn = document.text.toByteArray())
            }
        } catch (e: ExecutionException) {
            if (isUnitTestMode) throw e else return
        }

        writeInRunUndoTransparentAction { document.setText(processOutput.stdout) }
    }

    @Throws(ExecutionException::class)
    fun reformatCargoProject(
        cargoProject: CargoProject,
        owner: Disposable = cargoProject.project
    ) {
        cargoProject.project.computeWithCancelableProgress("Reformatting Cargo Project with Rustfmt...") {
            cargoProject.project.toolchain
                ?.cargoOrWrapper(cargoProject.workingDirectory)
                ?.toGeneralCommandLine(CargoCommandLine.forProject(cargoProject, "fmt", listOf("--all")))
                ?.execute(owner, false)
        }
    }

    private fun checkSupportForSkipChildrenFlag(cargoProject: CargoProject): Boolean {
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

    companion object {
        private val CONFIG_FILES: List<String> = listOf("rustfmt.toml", ".rustfmt.toml")

        private fun findConfigPathRecursively(directory: VirtualFile, stopAt: Path): Path? {
            val path = directory.pathAsPath
            if (!path.startsWith(stopAt) || path == stopAt) return null
            if (directory.children.any { it.name in CONFIG_FILES }) return path
            return findConfigPathRecursively(directory.parent, stopAt)
        }
    }
}
