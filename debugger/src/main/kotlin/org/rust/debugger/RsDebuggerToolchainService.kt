/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.google.common.annotations.VisibleForTesting
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.Decompressor
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager
import com.jetbrains.cidr.execution.debugger.backend.bin.UrlProvider
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import org.rust.RsBundle
import org.rust.openapiext.RsPathManager
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.name

@Service
class RsDebuggerToolchainService {

    fun debuggerAvailability(kind: DebuggerKind): DebuggerAvailability<*> {
        return when (kind) {
            DebuggerKind.LLDB -> lldbAvailability()
            DebuggerKind.GDB -> gdbAvailability()
        }
    }

    fun lldbAvailability(): DebuggerAvailability<LLDBBinaries> {
        if (LLDBDriverConfiguration.hasBundledLLDB()) return DebuggerAvailability.Bundled

        val (frameworkPath, frontendPath) = when {
            SystemInfo.isMac -> "LLDB.framework" to "LLDBFrontend"
            SystemInfo.isUnix -> "lib/liblldb.so" to "bin/LLDBFrontend"
            SystemInfo.isWindows -> "bin/liblldb.dll" to "bin/LLDBFrontend.exe"
            else -> return DebuggerAvailability.Unavailable
        }

        val lldbPath = lldbPath()
        val frameworkFile = lldbPath.resolve(frameworkPath)
        val frontendFile = lldbPath.resolve(frontendPath)
        if (!frameworkFile.exists() || !frontendFile.exists()) return DebuggerAvailability.NeedToDownload

        val versions = loadDebuggerVersions(DebuggerKind.LLDB)
        val (lldbFrameworkUrl, lldbFrontendUrl) = lldbUrls() ?: return DebuggerAvailability.Unavailable

        val lldbFrameworkVersion = fileNameWithoutExtension(lldbFrameworkUrl.toString())
        val lldbFrontendVersion = fileNameWithoutExtension(lldbFrontendUrl.toString())

        if (versions[LLDB_FRAMEWORK_PROPERTY_NAME] != lldbFrameworkVersion ||
            versions[LLDB_FRONTEND_PROPERTY_NAME] != lldbFrontendVersion) return DebuggerAvailability.NeedToUpdate

        return DebuggerAvailability.Binaries(LLDBBinaries(frameworkFile, frontendFile))
    }

    fun gdbAvailability(): DebuggerAvailability<GDBBinaries> {
        if (!isNewGdbSetupEnabled) return DebuggerAvailability.Unavailable
        // Even if we have bundled GDB, it still doesn't work on macOS for local runs
        if (SystemInfo.isMac) return DebuggerAvailability.Unavailable
        if (CidrDebuggerPathManager.getBundledGDBBinary().exists()) return DebuggerAvailability.Bundled

        val gdbBinaryPath = when {
            SystemInfo.isUnix -> "bin/gdb"
            SystemInfo.isWindows -> "bin/gdb.exe"
            else -> return DebuggerAvailability.Unavailable
        }

        val gdbFile = gdbPath().resolve(gdbBinaryPath)
        if (!gdbFile.exists()) return DebuggerAvailability.NeedToDownload

        val versions = loadDebuggerVersions(DebuggerKind.GDB)
        val gdbUrl = gdbUrl() ?: return DebuggerAvailability.Unavailable

        val gdbVersion = fileNameWithoutExtension(gdbUrl.toString())

        if (versions[GDB_PROPERTY_NAME] != gdbVersion) return DebuggerAvailability.NeedToUpdate

        return DebuggerAvailability.Binaries(GDBBinaries(gdbFile))
    }

    fun downloadDebugger(project: Project? = null, debuggerKind: DebuggerKind): DownloadResult {
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<DownloadResult, Nothing> {
            downloadDebuggerSynchronously(debuggerKind)
        }, RsBundle.message("dialog.title.download.debugger"), true, project)

        when (result) {
            is DownloadResult.Ok -> {
                Notifications.Bus.notify(Notification(
                    RUST_DEBUGGER_GROUP_ID,
                    RsBundle.message("notification.title.debugger"),
                    RsBundle.message("notification.content.debugger.successfully.downloaded"),
                    NotificationType.INFORMATION
                ))
            }
            is DownloadResult.Failed -> {
                Notifications.Bus.notify(Notification(
                    RUST_DEBUGGER_GROUP_ID,
                    RsBundle.message("notification.title.debugger"),
                    RsBundle.message("notification.content.debugger.downloading.failed"),
                    NotificationType.ERROR
                ))
            }
            else -> Unit
        }

        return result
    }

    private fun downloadDebuggerSynchronously(kind: DebuggerKind): DownloadResult {
        val baseDir = kind.basePath()
        val downloadableBinaries = when (kind) {
            DebuggerKind.LLDB -> {
                val (lldbFrameworkUrl, lldbFrontendUrl) = lldbUrls() ?: return DownloadResult.NoUrls
                listOf(
                    DownloadableDebuggerBinary(lldbFrameworkUrl.toString(), LLDB_FRAMEWORK_PROPERTY_NAME),
                    DownloadableDebuggerBinary(lldbFrontendUrl.toString(), LLDB_FRONTEND_PROPERTY_NAME)
                )
            }
            DebuggerKind.GDB -> {
                val gdbUrl = gdbUrl() ?: return DownloadResult.NoUrls
                listOf(DownloadableDebuggerBinary(gdbUrl.toString(), GDB_PROPERTY_NAME))
            }
        }

        return try {
            downloadAndUnarchive(baseDir, downloadableBinaries)
            DownloadResult.Ok(baseDir)
        } catch (e: IOException) {
            LOG.warn("Can't download debugger", e)
            DownloadResult.Failed(e.message)
        }
    }

    private fun lldbUrls(): Pair<URL, URL>? {
        val lldb = UrlProvider.lldb(OS.CURRENT, CpuArch.CURRENT) ?: return null
        val lldbFrontend = UrlProvider.lldbFrontend(OS.CURRENT, CpuArch.CURRENT) ?: return null
        return lldb to lldbFrontend
    }

    private fun gdbUrl(): URL? = UrlProvider.gdb(OS.CURRENT, CpuArch.CURRENT)

    @Throws(IOException::class)
    private fun downloadAndUnarchive(baseDir: Path, binariesToDownload: List<DownloadableDebuggerBinary>) {
        val service = DownloadableFileService.getInstance()

        val downloadDir = baseDir.toFile()
        downloadDir.deleteRecursively()

        val descriptions = binariesToDownload.map {
            service.createFileDescription(it.url)
        }

        val downloader = service.createDownloader(descriptions, "Debugger downloading")
        val downloadDirectory = downloadPath().toFile()
        val downloadResults = downloader.download(downloadDirectory)

        val versions = Properties()
        for (result in downloadResults) {
            val downloadUrl = result.second.downloadUrl
            val binaryToDownload = binariesToDownload.first { it.url == downloadUrl }
            val propertyName = binaryToDownload.propertyName
            val archiveFile = result.first
            Unarchiver.unarchive(archiveFile, downloadDir)
            archiveFile.delete()
            versions[propertyName] = fileNameWithoutExtension(downloadUrl)
        }

        saveVersionsFile(baseDir, versions)
    }

    private fun DownloadableFileService.createFileDescription(url: String): DownloadableFileDescription {
        val fileName = url.substringAfterLast("/")
        return createFileDescription(url, fileName)
    }

    private fun fileNameWithoutExtension(url: String): String {
        return url.substringAfterLast("/").removeSuffix(".zip").removeSuffix(".tar.gz")
    }

    @VisibleForTesting
    fun loadDebuggerVersions(kind: DebuggerKind): Properties = loadVersions(kind.basePath())

    @VisibleForTesting
    fun saveDebuggerVersions(kind: DebuggerKind, versions: Properties) {
        saveVersionsFile(kind.basePath(), versions)
    }

    private fun saveVersionsFile(basePath: Path, versions: Properties) {
        val file = basePath.resolve(DEBUGGER_VERSIONS).toFile()
        try {
            versions.store(file.bufferedWriter(), "")
        } catch (e: IOException) {
            LOG.warn("Failed to save `${basePath.name}/${file.name}`", e)
        }
    }

    private fun loadVersions(basePath: Path): Properties {
        val versions = Properties()
        val versionsFile = basePath.resolve(DEBUGGER_VERSIONS).toFile()

        if (versionsFile.exists()) {
            try {
                versionsFile.bufferedReader().use { versions.load(it) }
            } catch (e: IOException) {
                LOG.warn("Failed to load `${basePath.name}/${versionsFile.name}`", e)
            }
        }

        return versions
    }

    private fun DebuggerKind.basePath(): Path {
        val basePath = when (this) {
            DebuggerKind.LLDB -> lldbPath()
            DebuggerKind.GDB -> gdbPath()
        }
        return basePath
    }

    companion object {
        private val LOG: Logger = logger<RsDebuggerToolchainService>()

        private const val DEBUGGER_VERSIONS: String = "versions.properties"

        private const val LLDB_FRONTEND_PROPERTY_NAME = "lldbFrontend"
        private const val LLDB_FRAMEWORK_PROPERTY_NAME = "lldbFramework"
        private const val GDB_PROPERTY_NAME = "gdb"

        const val RUST_DEBUGGER_GROUP_ID = "Rust Debugger"

        private fun downloadPath(): Path = Paths.get(PathManager.getTempPath())
        private fun lldbPath(): Path = RsPathManager.pluginDirInSystem().resolve("lldb")
        private fun gdbPath(): Path = RsPathManager.pluginDirInSystem().resolve("gdb")

        fun getInstance(): RsDebuggerToolchainService = service()
    }

    @Suppress("unused")
    private enum class Unarchiver {
        ZIP {
            override val extension: String = "zip"
            override fun createDecompressor(file: File): Decompressor = Decompressor.Zip(file)
        },
        TAR {
            override val extension: String = "tar.gz"
            override fun createDecompressor(file: File): Decompressor = Decompressor.Tar(file)
        };

        protected abstract val extension: String
        protected abstract fun createDecompressor(file: File): Decompressor

        companion object {
            @Throws(IOException::class)
            fun unarchive(archivePath: File, dst: File) {
                val unarchiver = values().find { archivePath.name.endsWith(it.extension) }
                    ?: error("Unexpected archive type: $archivePath")
                unarchiver.createDecompressor(archivePath).extract(dst)
            }
        }
    }

    sealed class DownloadResult {
        class Ok(val baseDir: Path) : DownloadResult()
        object NoUrls : DownloadResult()
        class Failed(val message: String?) : DownloadResult()
    }

    private class DownloadableDebuggerBinary(
        val url: String,
        val propertyName: String,
    )
}
