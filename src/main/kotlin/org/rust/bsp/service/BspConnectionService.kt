/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.SourcesParams
import com.intellij.openapi.components.Service
import org.rust.bsp.BspClient
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.toolchain.impl.CargoMetadata
import java.util.concurrent.CompletableFuture

@Service
interface BspConnectionService {
    fun getBspServer(): BspServer

    fun getBspClient(): BspClient

    fun connect()

    fun disconnect()

    fun getProjectData(): CargoWorkspaceData

    fun compileSolution(params:CompileParams): CompletableFuture<CargoBuildResult>
    fun runSolution(): CompletableFuture<CargoBuildResult>
}
