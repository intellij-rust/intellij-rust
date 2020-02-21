/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkAdditionalData
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.execute
import org.rust.stdext.toPath
import java.io.File
import javax.swing.Icon

interface RsSdkFlavor {
    val name: String
    val icon: Icon get() = RsIcons.RUST

    fun suggestHomePaths(
        module: Module?,
        context: UserDataHolder?
    ): Collection<String> = emptyList()

    fun isValidSdkHome(path: String): Boolean {
        val file = File(path)
        return file.isFile && isValidSdkPath(file)
    }

    fun isValidSdkPath(file: File): Boolean

    fun getVersionString(sdkHome: String?): String? {
        if (sdkHome == null) return null
        val runDirectory = File(sdkHome).parent
        val processOutput = GeneralCommandLine(sdkHome.toPath(), "--version")
            .withWorkDirectory(runDirectory)
            .execute(timeoutInMilliseconds = 10000)
            ?: return null
        return parseVersionStringFromOutput(processOutput)
    }

    fun parseVersionStringFromOutput(processOutput: ProcessOutput): String? {
        if (processOutput.exitCode != 0) {
            var errors = processOutput.stderr
            if (errors.isEmpty()) {
                errors = processOutput.stdout
            }
            LOG.warn("Couldn't get toolchain version: process exited with code ${processOutput.exitCode}\n$errors")
            return null
        }
        return parseVersionStringFromOutput(processOutput.stderrLines)
            ?: parseVersionStringFromOutput(processOutput.stdoutLines)
    }

    fun parseVersionStringFromOutput(lines: List<String>): String? = lines.find { it.matches(VERSION_RE) }

    fun getSdkPath(path: VirtualFile?): VirtualFile? = path

    companion object {
        private val LOG: Logger = Logger.getInstance(RsSdkFlavor::class.java)
        private val VERSION_RE: Regex = "(cargo \\S+).*".toRegex()

        fun getApplicableFlavors(): Sequence<RsSdkFlavor> = sequenceOf(RustupSdkFlavor, CargoSdkFlavor)

        fun getFlavor(sdk: Sdk): RsSdkFlavor? {
            val flavor = (sdk.sdkAdditionalData as? RsSdkAdditionalData)?.flavor
            if (flavor != null) return flavor
            return getFlavor(sdk.homePath)
        }

        fun getFlavor(sdkPath: String?): RsSdkFlavor? {
            if (sdkPath == null) return null
            return getApplicableFlavors().find { flavor -> flavor.isValidSdkHome(sdkPath) }
        }
    }
}
