/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.BspConnectionDetails
import ch.epfl.scala.bsp4j.BuildClient
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.JavaBuildServer
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import kotlin.io.path.Path

class BspConnectionServiceImpl(val project: Project) : BspConnectionService {
    private var log = logger<BspConnectionServiceImpl>()

    private var bspServer: BspServer? = null

    override fun getBspServer(): BspServer {
        if (bspServer == null) {
            bspServer = getBspConnectionDetailsFile()
                ?.let { parseBspConnectionDetails(it) }
                ?.let { createBspServer(it) }
        }
        return bspServer!!
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

    private fun createBspClient(): BuildClient {
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BuildClient::class.java)) { _, method, args ->
            log.info("Calling method: ${method.name} with args: ${args?.joinToString()}")
            null
        } as BuildClient
    }

    private fun createBspServer(bspConnectionDetails: BspConnectionDetails): BspServer {
        val process = createAndStartProcess(bspConnectionDetails)
        val buildClient = createBspClient()
        val launcher = createLauncher(process.inputStream, process.outputStream, buildClient)

        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BspServer::class.java)) { _, method, args ->
            log.info("Calling method: ${method.name} with args: ${args?.joinToString()}")
            method.invoke(launcher.remoteProxy, *args.orEmpty())
        } as BspServer
    }

    private fun parseBspConnectionDetails(file: VirtualFile): BspConnectionDetails? =
        try {
            Gson().fromJson(VfsUtil.loadText(file), BspConnectionDetails::class.java)
        } catch (e: Exception) {
            log.info("Parsing file '$file' to BspConnectionDetails failed!", e)
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
