/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.CPPToolchainsConfigurable
import com.jetbrains.cidr.toolchains.OSType
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.cargo.runconfig.BuildResult.ToolchainError.*
import org.rust.debugger.runconfig.RsDebugRunnerUtils.ERROR_MESSAGE_TITLE

object RsCLionDebugRunnerUtils {

    fun checkToolchainSupported(host: String): ToolchainError? {
        val toolSet = CPPToolchains.getInstance().defaultToolchain?.toolSet ?: return null
        if (CPPToolchains.getInstance().osType == OSType.WIN) {
            val isMSVCRustToolchain = "msvc" in host
            val isGNURustToolchain = "gnu" in host

            if (isGNURustToolchain && toolSet.isMSVC) {
                return MSVCWithRustGNU
            }
            if (isMSVCRustToolchain && !toolSet.isMSVC) {
                return GNUWithRustMSVC
            }
        }
        return toolSet.isDebugSupportDisabled?.let { Other(it) }
    }

    fun checkToolchainConfigured(project: Project): Boolean {
        val toolchains = CPPToolchains.getInstance()
        // TODO: Fix synchronous execution on EDT
        val toolchain = toolchains.defaultToolchain
        if (toolchain == null) {
            showConfigureToolchainDialog(project, "Debug toolchain is not configured.")
            return false
        }
        return true
    }

    fun processInvalidToolchain(project: Project, toolchainError: ToolchainError) {
        when (toolchainError) {
            UnsupportedMSVC, UnsupportedGNU, is Other -> {
                Messages.showErrorDialog(project, toolchainError.message, ERROR_MESSAGE_TITLE)
            }
            MSVCWithRustGNU, GNUWithRustMSVC -> {
                showConfigureToolchainDialog(project, toolchainError.message)
            }
        }
    }

    private fun showConfigureToolchainDialog(project: Project, message: String) {
        val option = Messages.showDialog(
            project,
            message,
            ERROR_MESSAGE_TITLE,
            arrayOf("Configure"),
            Messages.OK,
            Messages.getErrorIcon()
        )
        if (option == Messages.OK) {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                CPPToolchainsConfigurable::class.java,
                null
            )
        }
    }
}
