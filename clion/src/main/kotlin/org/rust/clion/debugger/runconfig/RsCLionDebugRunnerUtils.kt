/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.CPPToolchainsConfigurable
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.debugger.runconfig.RsDebugRunnerUtils.ERROR_MESSAGE_TITLE
import org.rust.debugger.runconfig.RsDebugRunnerUtils.MSVC_IS_NOT_SUPPORTED_MESSAGE
import org.rust.openapiext.computeWithCancelableProgress

object RsCLionDebugRunnerUtils {

    fun checkToolchainSupported(project: Project, state: CargoRunStateBase): Boolean {
        if (!SystemInfo.isWindows) return true
        val isMsvc = project.computeWithCancelableProgress("Checking if toolchain is supported...") {
            "msvc" in state.rustVersion().rustc?.host.orEmpty()
        }
        if (isMsvc) {
            Messages.showErrorDialog(project, MSVC_IS_NOT_SUPPORTED_MESSAGE, ERROR_MESSAGE_TITLE)
            return false
        }
        return true
    }

    fun checkToolchainConfigured(project: Project): Boolean {
        val toolchains = CPPToolchains.getInstance()
        // TODO: Fix synchronous execution on EDT
        val toolchain = toolchains.defaultToolchain
        if (toolchain == null) {
            val option = Messages.showDialog(
                project,
                "Debug toolchain is not configured.",
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
            return false
        }
        return true
    }
}
