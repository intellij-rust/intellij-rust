/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.execute
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Path

class Rustfmt(private val rustfmtExecutable: Path) {

    @Throws(ExecutionException::class)
    fun reformatFile(
        project: Project,
        file: VirtualFile,
        owner: Disposable = project
    ): ProcessOutput {
        val channel = project.cargoProjects.findProjectForFile(file)
            ?.rustcInfo?.version?.channel

        val arguments = mutableListOf<String>()
        val (emit, skipChildren) = checkSupportForRustfmtFlags(file.parent.pathAsPath)
        arguments += if (emit) "--emit=files" else "--write-mode=overwrite"
        if (project.rustSettings.useSkipChildren && channel == RustChannel.NIGHTLY && skipChildren) {
            arguments += "--unstable-features"
            arguments += "--skip-children"
        }
        arguments += file.path
        return GeneralCommandLine(rustfmtExecutable)
            .withWorkDirectory(file.parent.pathAsPath)
            .withParameters(arguments)
            .execute(owner, false)
    }

    private fun checkSupportForRustfmtFlags(workingDirectory: Path): RustfmtFlags {
        val lines = GeneralCommandLine(rustfmtExecutable)
            .withParameters("-h")
            .withWorkDirectory(workingDirectory)
            .execute()
            ?.stdoutLines
            ?: return RustfmtFlags(false, false)

        return RustfmtFlags(lines.any { it.contains(" --emit ") },
            lines.any { it.contains(" --skip-children ") })
    }

    private data class RustfmtFlags(val emit: Boolean, val skipChildren: Boolean)
}
