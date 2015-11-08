package org.rust.lang

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import org.rust.lang.icons.RustIcons
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

class RustSdkType : SdkType("Rust SDK") {

    override fun getPresentableName() = name

    override fun getIcon() = RustIcons.FILE
    override fun getIconForAddAction() = icon

    override fun suggestHomePath(): String? {
        if (SystemInfo.isMac) {
            // look for rust installed by homebrew
            val homebrew = File("/usr/local/Cellar/rust")
            if (homebrew.exists())
                return homebrew.absolutePath
        }

        return null
    }

    override fun isValidSdkHome(path: String): Boolean {
        val rustc = getSdkExecutable(path, "rustc")
        val cargo = getSdkExecutable(path, "cargo")
        return rustc.canExecute() && cargo.canExecute()
    }

    override fun adjustSelectedSdkHome(homePath: String?): String? {
        val file = File(homePath)
        return when (file.nameWithoutExtension) {
            "rustc", "cargo" -> file.parentFile.absolutePath
            "bin"            -> file.parentFile.absolutePath
            else             -> super.adjustSelectedSdkHome(homePath)
        }
    }

    private fun getSdkExecutable(sdkHome: String, command: String): File {
        return File(File(sdkHome, "bin"), getExecutableFileName(command))
    }

    private fun getExecutableFileName(executableName: String): String {
        return if (SystemInfo.isWindows) "$executableName.exe" else executableName
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String) =
            getVersionString(sdkHome)?.let { "Rust $it" }
                    ?: "Unknown Rust version at $sdkHome"

    override fun getVersionString(sdkHome: String): String? {
        val rustc = getSdkExecutable(sdkHome, "rustc")
        if (!rustc.canExecute()) {
            val reason = "${rustc.path}${if (rustc.exists()) " is not executable." else " is missing."}"
            LOG.warn("Can't detect rustc version: $reason")
            return null
        }

        try {
            val cmd = GeneralCommandLine()
                    .withWorkDirectory(sdkHome)
                    .withExePath(rustc.absolutePath)
                    .withParameters("--version")

            val output = CapturingProcessHandler(cmd.createProcess()).runProcess(10 * 1000)
            if (output.exitCode != 0 || output.isCancelled || output.isTimeout)
                return null

            val line = output.stdoutLines.firstOrNull()
            LOG.debug("rustc --version returned: $line")

            val matcher = RE_VERSION.matcher(line)
            if (!matcher.matches())
                return null

            return matcher.group(1)

        } catch (e: ExecutionException) {
            LOG.warn(e);
            return null;
        }
    }

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator) = null
    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
    }

    companion object {
        val LOG = Logger.getInstance(RustSdkType::class.java)
        val RE_VERSION = Pattern.compile("rustc (\\d+\\.\\d+\\.\\d).*")

        fun getInstance() = SdkType.findInstance(RustSdkType::class.java)
    }
}
