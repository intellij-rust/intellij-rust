/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.rust.bsp.BspClient
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class BspConnectionServiceImpl(val project: Project) : BspConnectionService {

    private var bspServer: BspServer? = null
    private var bspClient: BspClient? = null

    override fun getBspServer(): BspServer {
        createBspServerIfNull()
        return bspServer!!
    }

    override fun getBspClient(): BspClient {
        createBspServerIfNull()
        return bspClient!!
    }

    override fun doStaff() {
        try {
            val server = getBspServer()
            val initializeBuildResult =
                queryForInitialize(server).catchSyncErrors { println("Error while initializing BSP server $it") }
                    .orTimeout(10, TimeUnit.SECONDS).get()
            println("BSP server initialized: $initializeBuildResult")

            val projectDetails = calculateProjectDetailsWithCapabilities(server, initializeBuildResult.capabilities) {
                println("BSP server capabilities: $it")
            }

            println("BSP project details: $projectDetails")
        } catch (e: Exception) {
            println("Error while initializing BSP server: ${e.message}")
        }
    }

    private fun createBspServerIfNull() {
        if (bspServer == null) {
            bspServer = getBspConnectionDetailsFile()
                ?.let { parseBspConnectionDetails(it) }
                ?.let { createBspServer(it) }!!
        }
    }

    private fun queryForInitialize(server: BspServer): CompletableFuture<InitializeBuildResult> {
        val buildParams = createInitializeBuildParams()
        return server.buildInitialize(buildParams)
    }

    private fun createInitializeBuildParams(): InitializeBuildParams {
        val projectBaseDir = project.basePath
        val params = InitializeBuildParams(
            "IntelliJ-Rust",
            "0.4.0",
            "2.0.0",
            projectBaseDir.toString(),
            BuildClientCapabilities(listOf("java"))
        )
        println("InitializeBuildParams: $params")
        val dataJson = JsonObject()
        dataJson.addProperty("clientClassesRootDir", "$projectBaseDir/out")
        params.data = dataJson

        return params
    }

    private fun createLauncher(bspIn: InputStream, bspOut: OutputStream, client: BuildClient): Launcher<BspServer> =
        Launcher.Builder<BspServer>()
            .setRemoteInterface(BspServer::class.java)
            .setExecutorService(AppExecutorUtil.getAppExecutorService())
            .setInput(bspIn)
            .setOutput(bspOut)
            .setLocalService(client)
            .create()

    private fun createAndStartProcess(bspConnectionDetails: BspConnectionDetails): Process =
        ProcessBuilder(bspConnectionDetails.argv)
            .directory(project.stateStore.projectBasePath.toFile())
            .withRealEnvs()
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    private fun createBspClient(): BspClient {
        return BspClient()
    }

    private fun createBspServer(bspConnectionDetails: BspConnectionDetails): BspServer {
        val process = createAndStartProcess(bspConnectionDetails)
        val bspClient = createBspClient()
        val launcher = createLauncher(process.inputStream, process.outputStream, bspClient)

        this.bspClient = bspClient

        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BspServer::class.java)) { _, method, args ->
            println("Calling method: ${method.name} with args: ${args?.joinToString()}")
            method.invoke(launcher.remoteProxy, *args.orEmpty())
        } as BspServer
    }

    private fun parseBspConnectionDetails(file: VirtualFile): BspConnectionDetails? =
        try {
            Gson().fromJson(VfsUtil.loadText(file), BspConnectionDetails::class.java)
        } catch (e: Exception) {
            println("Parsing file '$file' to BspConnectionDetails failed! ${e.message}")
            null
        }

    private fun getBspConnectionDetailsFile(): VirtualFile? =
        "${project.stateStore.projectBasePath}/.bsp/bazelbsp.json".toVirtualFile()

    private fun String.toVirtualFile(): VirtualFile? =
        VirtualFileManager.getInstance().findFileByNioPath(Path(this))
}

interface BspServer : BuildServer, JavaBuildServer

fun ProcessBuilder.withRealEnvs(): ProcessBuilder {
    val env = environment()
    env.clear()
    env.putAll(EnvironmentUtil.getEnvironmentMap())

    return this
}


fun calculateProjectDetailsWithCapabilities(
    server: BspServer,
    buildServerCapabilities: BuildServerCapabilities,
    errorCallback: (Throwable) -> Unit
): ProjectDetails {
    val workspaceBuildTargetsResult = queryForBuildTargets(server).get()

    val allTargetsIds = calculateAllTargetsIds(workspaceBuildTargetsResult)

    val sourcesFuture = queryForSourcesResult(server, allTargetsIds).catchSyncErrors(errorCallback)
    val resourcesFuture =
        queryForTargetResources(server, buildServerCapabilities, allTargetsIds)?.catchSyncErrors(errorCallback)
    val dependencySourcesFuture =
        queryForDependencySources(server, buildServerCapabilities, allTargetsIds)?.catchSyncErrors(errorCallback)
    val javacOptionsFuture = queryForJavacOptions(server, allTargetsIds).catchSyncErrors(errorCallback)

    return ProjectDetails(
        targetsId = allTargetsIds,
        targets = workspaceBuildTargetsResult.targets.toSet(),
        sources = sourcesFuture.get().items,
        resources = resourcesFuture?.get()?.items ?: emptyList(),
        dependenciesSources = dependencySourcesFuture?.get()?.items ?: emptyList(),
        // SBT seems not to support the javacOptions endpoint and seems just to hang when called,
        // so it's just safer to add timeout here. This should not be called at all for SBT.
        javacOptions = javacOptionsFuture.get()?.items ?: emptyList()
    )
}


private fun queryForBuildTargets(server: BspServer): CompletableFuture<WorkspaceBuildTargetsResult> =
    server.workspaceBuildTargets()

private fun calculateAllTargetsIds(workspaceBuildTargetsResult: WorkspaceBuildTargetsResult): List<BuildTargetIdentifier> =
    workspaceBuildTargetsResult.targets.map { it.id }

private fun queryForSourcesResult(
    server: BspServer,
    allTargetsIds: List<BuildTargetIdentifier>
): CompletableFuture<SourcesResult> {
    val sourcesParams = SourcesParams(allTargetsIds)

    return server.buildTargetSources(sourcesParams)
}

private fun queryForTargetResources(
    server: BspServer,
    capabilities: BuildServerCapabilities,
    allTargetsIds: List<BuildTargetIdentifier>
): CompletableFuture<ResourcesResult>? {
    val resourcesParams = ResourcesParams(allTargetsIds)

    return if (capabilities.resourcesProvider) server.buildTargetResources(resourcesParams)
    else null
}

private fun queryForDependencySources(
    server: BspServer,
    capabilities: BuildServerCapabilities,
    allTargetsIds: List<BuildTargetIdentifier>
): CompletableFuture<DependencySourcesResult>? {
    val dependencySourcesParams = DependencySourcesParams(allTargetsIds)

    return if (capabilities.dependencySourcesProvider) server.buildTargetDependencySources(dependencySourcesParams)
    else null
}

private fun queryForJavacOptions(
    server: BspServer,
    allTargetsIds: List<BuildTargetIdentifier>
): CompletableFuture<JavacOptionsResult> {
    val javacOptionsParams = JavacOptionsParams(allTargetsIds)
    return server.buildTargetJavacOptions(javacOptionsParams)
}


private fun <T> CompletableFuture<T>.catchSyncErrors(errorCallback: (Throwable) -> Unit): CompletableFuture<T> =
    this.whenComplete { _, exception ->
        exception?.let { errorCallback(it) }
    }

