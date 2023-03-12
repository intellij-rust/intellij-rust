/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.*
import com.google.common.hash.HashCode
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.rust.bsp.BspClient
import org.rust.bsp.BspConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.impl.CargoMetadata
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class BspConnectionServiceImpl(val project: Project) : BspConnectionService {

    private var bspServer: BspServer? = null
    private var bspClient: BspClient? = null
    private var disconnectActions: MutableList<() -> Unit> = mutableListOf()

    override fun getBspServer(): BspServer {
        createBspServerIfNull()
        return bspServer!!
    }

    override fun getBspClient(): BspClient {
        createBspServerIfNull()
        return bspClient!!
    }

    override fun connect() {
        println("Starting BSP server")
        getBspServer()
    }

    override fun getProjectData(): CargoWorkspaceData {
        val server = getBspServer()
        val initializeBuildResult =
            queryForInitialize(server).catchSyncErrors { println("Error while initializing BSP server $it") }.get()
        server.onBuildInitialized()

        return calculateProjectDetailsWithCapabilities(server, initializeBuildResult.capabilities) {
            println("BSP server capabilities: $it")
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
        val dataJson = JsonObject()
        dataJson.addProperty("clientClassesRootDir", "$projectBaseDir/out")
        params.data = dataJson

        return params
    }

    override fun disconnect() {
        val exceptions = disconnectActions.mapNotNull { executeDisconnectActionAndReturnThrowableIfFailed(it) }
        disconnectActions.clear()
        throwExceptionWithSuppressedIfOccurred(exceptions)

        bspServer = null
        bspClient = null
    }

    private fun executeDisconnectActionAndReturnThrowableIfFailed(disconnectAction: () -> Unit): Throwable? =
        try {
            disconnectAction()
            null
        } catch (e: Exception) {
            e
        }

    private fun throwExceptionWithSuppressedIfOccurred(exceptions: List<Throwable>) {
        val firstException = exceptions.firstOrNull()

        if (firstException != null) {
            exceptions
                .drop(1)
                .forEach { firstException.addSuppressed(it) }

            throw firstException
        }
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

        disconnectActions.add { bspServer?.buildShutdown() }
        disconnectActions.add { bspServer?.onBuildExit() }

        disconnectActions.add { process.waitFor(3, TimeUnit.SECONDS) }
        disconnectActions.add { process.destroy() }

        val bspClient = createBspClient()

        val bspIn = process.inputStream
        disconnectActions.add { bspIn.close() }

        val bspOut = process.outputStream
        disconnectActions.add { bspOut.close() }

        val launcher = createLauncher(bspIn, bspOut, bspClient)
        val listening = launcher.startListening()
        disconnectActions.add { listening.cancel(true) }

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

interface BspServer : BuildServer, RustBuildServer

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
): CargoWorkspaceData {
    val projectBazelTargets = queryForBazelTargets(server).get()
    val bspWorkspaceRoot = projectBazelTargets.targets.find { it.id.uri == BspConstants.BSP_WORKSPACE_ROOT_URI}
    projectBazelTargets.targets.removeAll { it.id.uri == BspConstants.BSP_WORKSPACE_ROOT_URI }
    val projectWorkspaceData = queryForWorkspaceData(server).get()

    val projectPackages = createPackage(projectWorkspaceData, projectBazelTargets)
    val dependencies = createDependencies(projectWorkspaceData)
    val rawPackages = createRawDependencies(projectWorkspaceData)
    val workspaceRoot = bspWorkspaceRoot?.baseDirectory

    return CargoWorkspaceData(projectPackages, dependencies, rawPackages, workspaceRoot)
}

private fun createCfgOptions(cfgOptions: RustCfgOptions): CfgOptions
{
    val name = cfgOptions.nameOptions.toSet()
    val keyValueOptions = cfgOptions.keyValueOptions.associate { Pair(it.key, it.value.toSet()) }
    return CfgOptions(keyValueOptions, name)
}
private fun resolveEdition(edition: String): CargoWorkspace.Edition
{
    return when (edition)
    {
        "2015" -> CargoWorkspace.Edition.EDITION_2015
        "2018" -> CargoWorkspace.Edition.EDITION_2018
        "2021" -> CargoWorkspace.Edition.EDITION_2021
        else -> CargoWorkspace.Edition.EDITION_2021
    }
}

private fun resolveTargetKind(targetKind: String): CargoWorkspace.TargetKind
{
    return when (targetKind)
    {
        "application" -> CargoWorkspace.TargetKind.Bin
        "test" -> CargoWorkspace.TargetKind.Test
        "library" -> CargoWorkspace.TargetKind.Lib()
        else -> CargoWorkspace.TargetKind.Unknown
    }
}

private fun resolveOrigin(targetKind: String): PackageOrigin
{
    return when (targetKind)
    {
        "stdlib" -> PackageOrigin.STDLIB
        "workspace" -> PackageOrigin.WORKSPACE
        "dep" -> PackageOrigin.DEPENDENCY
        "stdlib-dep" -> PackageOrigin.STDLIB_DEPENDENCY
        else -> PackageOrigin.WORKSPACE
    }
}

fun createPackage(projectWorkspaceData: RustWorkspaceResult, projectBazelTargets: WorkspaceBuildTargetsResult): List<CargoWorkspaceData.Package>
{
    val idToWorkspace = projectWorkspaceData.packages.associateBy { it.id.uri }
    val packages = emptyList<CargoWorkspaceData.Package>().toMutableList()
    for (project in projectBazelTargets.targets) {
        val id = project.id.uri
        if (id !in idToWorkspace)
            continue;
        val workspace = idToWorkspace[id]!!
        var targets = idToWorkspace[id]?.targets?.map { CargoWorkspaceData.Target(it.crateRootUrl, it.name, resolveTargetKind(it.kind), resolveEdition(it.edition), it.isDoctest, it.requiredFeatures) }
        if (targets.isNullOrEmpty())
            targets = emptyList()
        val origin = resolveOrigin(workspace.origin)
        val edition = resolveEdition(workspace.edition)
        val features = workspace.features.associate { Pair(it.name, it.deps) }
        val enabledFeatures = workspace.enabledFeatures.toSet()
        val cfgOptions = createCfgOptions(workspace.cfgOptions)
        val env = workspace.env.associate { Pair(it.name, it.value) }
        val procMacroArtifact = CargoWorkspaceData.ProcMacroArtifact(Path(workspace.procMacroArtifact.path), org.rust.stdext.HashCode.fromHexString(workspace.procMacroArtifact.path))
        packages.add(CargoWorkspaceData.Package(id, project.baseDirectory, project.displayName, workspace.version, targets, workspace.source, origin, edition, features, enabledFeatures, cfgOptions, env, workspace.outDirUrl, procMacroArtifact))
    }
    return packages
}

private fun resolveDependency(dependencyType: String): CargoWorkspace.DepKind
{
    return when (dependencyType)
    {
        "build" -> CargoWorkspace.DepKind.Build
        "development" -> CargoWorkspace.DepKind.Development
        "normal" -> CargoWorkspace.DepKind.Normal
        "stdlib" -> CargoWorkspace.DepKind.Stdlib
        else -> CargoWorkspace.DepKind.Unclassified
    }
}

fun createDependencies(projectWorkspaceData: RustWorkspaceResult):  Map<PackageId, Set<CargoWorkspaceData.Dependency>>
{
    val dependencies = projectWorkspaceData.dependencies.map { Pair<PackageId, CargoWorkspaceData.Dependency>(it.source, CargoWorkspaceData.Dependency(it.source, it.target, it.depKinds.map { it2 -> CargoWorkspace.DepKindInfo(resolveDependency(it2.kind), it2.target) })) }
    val dependencyMap = emptyMap<PackageId, MutableSet<CargoWorkspaceData.Dependency>>().toMutableMap()
    for (pair in dependencies) {
        if (pair.first !in dependencyMap) {
            dependencyMap[pair.first] = emptySet<CargoWorkspaceData.Dependency>().toMutableSet()
        }
        dependencyMap[pair.first]?.add(pair.second)
    }
    return dependencyMap
}

fun createRawDependencies(projectWorkspaceData: RustWorkspaceResult): Map<PackageId, List<CargoMetadata.RawDependency>>
{
    val dependencies = projectWorkspaceData.rawDependencies.map { Pair(it.packageId, CargoMetadata.RawDependency(it.name, null, it.kind, it.target, it.isOptional, it.isUses_default_features, it.features)) }
    val rawMap = emptyMap<PackageId, MutableList<CargoMetadata.RawDependency>>().toMutableMap()
    for (pair in dependencies) {
        if (pair.first !in rawMap) {
            rawMap[pair.first] = emptyList<CargoMetadata.RawDependency>().toMutableList()
        }
        rawMap[pair.first]?.add(pair.second);
    }
    return rawMap
}


fun queryForWorkspaceData(server: BspServer): CompletableFuture<RustWorkspaceResult> {
    return server.rustWorkspace()
}

fun queryForBazelTargets(server: BspServer): CompletableFuture<WorkspaceBuildTargetsResult> {
    return server.workspaceBuildTargets()
}

private fun <T> CompletableFuture<T>.catchSyncErrors(errorCallback: (Throwable) -> Unit): CompletableFuture<T> =
    this.whenComplete { _, exception ->
        exception?.let { errorCallback(it) }
    }

