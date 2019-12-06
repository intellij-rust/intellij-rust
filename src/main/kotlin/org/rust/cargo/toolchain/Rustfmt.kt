/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.text.SemVer
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.lang.core.psi.ext.edition
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.nio.file.Path

class Rustfmt(private val rustfmtExecutable: Path) {

    @Throws(ExecutionException::class)
    fun reformatDocumentText(cargoProject: CargoProject, document: Document): String? {
        val file = document.virtualFile ?: return null
        if (file.isNotRustFile || !file.isValid) return null

        val arguments = buildList<String> {
            add("--emit=stdout")

            if (checkSupportForSkipChildrenFlag(cargoProject)) {
                add("--unstable-features")
                add("--skip-children")
            }

            val configPath = findConfigPathRecursively(file.parent, stopAt = cargoProject.workingDirectory)
            if (configPath != null) {
                add("--config-path")
                add(configPath.toString())
            }

            val currentRustcVersion = cargoProject.rustcInfo?.version?.semver
            if (currentRustcVersion != null && currentRustcVersion >= RUST_1_31) {
                val edition = runReadAction {
                    val psiFile = file.toPsiFile(cargoProject.project)
                    psiFile?.edition ?: CargoWorkspace.Edition.EDITION_2018
                }
                add("--edition=${edition.presentation}")
            }
        }

        val processOutput = try {
            GeneralCommandLine(rustfmtExecutable)
                .withWorkDirectory(cargoProject.workingDirectory)
                .withParameters(arguments)
                .withCharset(Charsets.UTF_8)
                .execute(cargoProject.project, false, stdIn = document.text.toByteArray())
        } catch (e: ExecutionException) {
            if (isUnitTestMode) throw e else return null
        }

        return processOutput.stdout
    }

    @Throws(ExecutionException::class)
    fun reformatCargoProject(
        cargoProject: CargoProject,
        owner: Disposable = cargoProject.project
    ) {
        val project = cargoProject.project
        project.computeWithCancelableProgress("Reformatting Cargo Project with Rustfmt...") {
            project.toolchain
                ?.cargoOrWrapper(cargoProject.workingDirectory)
                ?.toGeneralCommandLine(project, CargoCommandLine.forProject(cargoProject, "fmt", listOf("--all")))
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
        private val RUST_1_31: SemVer = SemVer.parseFromText("1.31.0")!!
        private val CONFIG_FILES: List<String> = listOf("rustfmt.toml", ".rustfmt.toml")

        private fun findConfigPathRecursively(directory: VirtualFile, stopAt: Path): Path? {
            val path = directory.pathAsPath
            if (!path.startsWith(stopAt) || path == stopAt) return null
            if (directory.children.any { it.name in CONFIG_FILES }) return path
            return findConfigPathRecursively(directory.parent, stopAt)
        }
    }
}
