/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.RunParams
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.toolchain.impl.RustcVersion
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@Service
interface BspConnectionService : Disposable {
    fun isConnected(): Boolean
    fun connect()

    fun disconnect()

    fun getProjectData(projectDirectory: Path): CargoWorkspaceData

    fun compileAllSolutions(params: CompileParams): CompletableFuture<CargoBuildResult>
    fun compileSolution(params: CompileParams): CompletableFuture<CargoBuildResult>
    fun runSolution(params: RunParams): CompletableFuture<CargoBuildResult>

    fun hasBspServer(): Boolean

    fun getMacroResolverPath(): Path?

    fun getStdLibPath(): VirtualFile?

    fun getRustcVersion(): RustcVersion?

    fun getRustcSysroot(): String?

    fun getBspTargets(): List<BuildTarget>
    /*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

    companion object {
        val BSP_WORKSPACE_REFRESH_TOPIC: Topic<BspProjectsRefreshListener> = Topic(
            "bsp workspace refresh",
            BspProjectsRefreshListener::class.java
        )

    }

    fun interface BspProjectsRefreshListener {
        fun onRefreshFinished()
    }


}
