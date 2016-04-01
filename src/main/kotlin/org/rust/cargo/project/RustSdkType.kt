package org.rust.cargo.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import org.rust.cargo.util.PlatformUtil
import org.rust.ide.icons.RustIcons
import java.io.File
import java.util.concurrent.ExecutionException

class RustSdkType : SdkType("Rust SDK") {

    override fun getPresentableName() = name

    override fun getIcon() = RustIcons.RUST
    override fun getIconForAddAction() = icon

    override fun suggestHomePath(): String? {
        if (SystemInfo.isUnix) {
            // Check whether there is multi-rust installation
            val root = File(FileUtil.expandUserHome("~/.multirust/toolchains"))
            if (root.exists()) {
                listOf("stable", "beta", "nightly").forEach {
                    val multiSub = File(root, it)
                    if (multiSub.exists()) {
                        return multiSub.absolutePath
                    }
                }
            }

            // Check Homebrew's cellar for the rust binaries
            // pre-installed
            if (SystemInfo.isMac) {
                val cellar = File("/usr/local/Cellar/rust")
                if (isValidSdkHome(cellar)) {
                    return cellar.absolutePath
                }
            }

            // Fallback to the local typical option of `make install`
            val local = File("/usr/local/")
            if (isValidSdkHome(local)) {
                return local.absolutePath
            }
        } else if (SystemInfo.isWindows) {
            val programFiles = File(System.getenv("ProgramFiles"))
            if (!programFiles.exists() || !programFiles.isDirectory)
                return null

            return programFiles  .listFiles     { file -> file.isDirectory }
                                ?.filter        { path -> path.nameWithoutExtension.toLowerCase().startsWith("rust") && isValidSdkHome(path) }
                                ?.firstOrNull()
                                ?.absolutePath
        }

        return tryFindSDKHomeInPATH()
    }

    private fun tryFindSDKHomeInPATH(): String? {
        val PATH = System.getenv("PATH") ?: return null

        for (path in PATH.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }) {
            val dir = File(path)
            if (!dir.isDirectory)
                continue

            val candidate = dir.parentFile // $RUST_HOME/bin
            if (isValidSdkHome(candidate)) {
                return candidate.absolutePath
            }
        }

        return null
    }

    override fun isValidSdkHome(path: String): Boolean = isValidRustCHome(path)

    private fun isValidSdkHome(path: File): Boolean = isValidRustCHome(path)

    fun isValidCargoHome(path: String) = isValidCargoHome(File(path))

    private fun isValidCargoHome(path: File) = getPathToExecInSDK(path, CARGO_BINARY_NAME).canExecute()

    fun isValidRustCHome(path: String) = isValidRustCHome(File(path))

    private fun isValidRustCHome(path: File) = getPathToExecInSDK(path, RUSTC_BINARY_NAME).canExecute()

    override fun adjustSelectedSdkHome(homePath: String): String {
        val file = File(homePath)
        return when (file.nameWithoutExtension) {
            BIN_DIR -> file.parentFile.absolutePath
            else    -> super.adjustSelectedSdkHome(homePath)
        }
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String) =
        getVersionString(sdkHome)?.let { "Rust $it" } ?: "Rust"

    override fun getHomeChooserDescriptor(): FileChooserDescriptor {
        val suggested = suggestHomePath()
        return super.getHomeChooserDescriptor()
                    .withShowHiddenFiles(suggested != null && isMultiRust(suggested))
    }

    override fun getVersionString(sdkHome: String): String? {
        val fullVersion = RustcVersion.queryFromRustc(sdkHome)
        return fullVersion?.release
    }

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel,
                                                  sdkModificator: SdkModificator): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    companion object {
        fun getPathToBinDirInSDK(sdkHome: File): File = File(sdkHome, BIN_DIR)
        fun getPathToBinDirInSDK(sdkHome: String): File = getPathToBinDirInSDK(File(sdkHome))

        fun getPathToLibDirInSDK(sdkHome: File): File = File(sdkHome, LIB_DIR)
        fun getPathToLibDirInSDK(sdkHome: String): File = getPathToLibDirInSDK(File(sdkHome))

        fun getPathToExecInSDK(sdkHome: File, fileName: String): File =
            File(getPathToBinDirInSDK(sdkHome), PlatformUtil.getCanonicalNativeExecutableName(fileName))

        fun getPathToExecInSDK(sdkHome: String, fileName: String): File =
            getPathToExecInSDK(File(sdkHome), fileName)

        val INSTANCE by lazy {
            SdkType.findInstance(RustSdkType::class.java)
        }


        private val BIN_DIR = "bin"
        private val LIB_DIR = "lib"

        internal val RUSTC_BINARY_NAME = "rustc"
        internal val CARGO_BINARY_NAME = "cargo"
    }
}

val Module.pathToCargo: String? get()  {
    val sdk = rustSdk ?: return null
    return sdk.homePath?.let { RustSdkType.getPathToExecInSDK(it, RustSdkType.CARGO_BINARY_NAME).absolutePath }
}

val Module.rustSdk: Sdk? get() {
    val moduleSdk = ModuleRootManager.getInstance(this).sdk
    if (moduleSdk.isRustSdk) {
        return moduleSdk
    }

    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (projectSdk.isRustSdk) {
        return projectSdk
    }

    return null
}

/* Parsed result of `rustc -vV` output, which looks like this
 *
 * ```
 *
 * rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
 * binary: rustc
 * commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
 * commit-date: 2016-03-02
 * host: x86_64-unknown-linux-gnu
 * release: 1.8.0-beta.1
 * ```
 */
data class RustcVersion(
    val commitHash: String,
    val release: String,
    val isStable: Boolean
) {
    val sourcesArchiveUrl: String get() {
        // We download sources from github and not from rust-lang.org, because we want zip archives. rust-lang.org
        // hosts only .tar.gz.
        val tag = if (isStable) release else commitHash
        return "https://github.com/rust-lang/rust/archive/$tag.zip"
    }

    companion object {
        fun queryFromRustc(sdkHome: String): RustcVersion? {
            val cmd = createRustcCommandLine(sdkHome)

            val procOut = try {
                CapturingProcessHandler(cmd.createProcess(), Charsets.UTF_8, cmd.commandLineString).runProcess(10 * 1000)
            } catch (e: ExecutionException) {
                log.warn("Failed to detect `rustc` version!", e)
                return null
            }

            if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout) {
                return null
            }

            return parseLines(procOut.stdoutLines)
        }

        private fun createRustcCommandLine(sdkHome: String): GeneralCommandLine {
            val rustc = RustSdkType.getPathToExecInSDK(sdkHome, RustSdkType.RUSTC_BINARY_NAME)
            return  GeneralCommandLine()
                .withWorkDirectory(sdkHome)
                .withExePath(rustc.absolutePath)
                .withParameters("--version", "--verbose")
        }

        private fun parseLines(lines: List<String>): RustcVersion? {
            // We want to parse here
            // rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
            // binary: rustc
            // commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
            // commit-date: 2016-03-02
            // host: x86_64-unknown-linux-gnu
            // release: 1.8.0-beta.1
            val commitHashRe = "commit-hash: (.*)".toRegex()
            val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
            val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

            val commitHash = find(commitHashRe)?.let { it.groups[1]!!.value } ?: return null
            val releaseMatch = find(releaseRe) ?: return null
            val release = releaseMatch.groups[1]!!.value
            val isStable = releaseMatch.groups[2]!!.value.isEmpty()

            return RustcVersion(
                commitHash,
                release,
                isStable
            )
        }

        private val log = Logger.getInstance(RustcVersion::class.java)
    }
}

private val Sdk?.isRustSdk: Boolean get() =
    this != null && sdkType == RustSdkType.INSTANCE

private fun isMultiRust(sdkHome: String): Boolean = ".multirust" in sdkHome

