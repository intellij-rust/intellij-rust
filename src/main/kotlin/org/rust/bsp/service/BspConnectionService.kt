/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.RunParams
import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.toolchain.impl.RustcVersion
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@Service
interface BspConnectionService {
    fun isConnected(): Boolean
    fun connect()

    fun disconnect()

    fun getProjectData(projectDirectory: Path): CargoWorkspaceData

    fun compileSolution(params:CompileParams): CompletableFuture<CargoBuildResult>
    fun runSolution(params: RunParams): CompletableFuture<CargoBuildResult>

    fun hasBspServer(): Boolean

    fun getMacroResolverPath(): Path?

    fun getStdLibPath(): VirtualFile?

    fun getRustcVersion(): RustcVersion?

    fun getRustcSysroot(): String?
}
