/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.debugger.isNewGdbSetupEnabled
import org.rust.debugger.runconfig.RsDebugRunnerBase

class RsCLionDebugRunner : RsDebugRunnerBase() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        // If `org.rust.debugger.gdb.setup.v2` is enabled,
        // the plugin shouldn't use `CPPToolchains` for debugging session setup.
        // So, this should be disabled in this case because its main purpose is to use `CPPToolchains` in CLion
        return super.canRun(executorId, profile) && !isNewGdbSetupEnabled
    }

    override fun checkToolchainSupported(project: Project, host: String): ToolchainError? =
        RsCLionDebugRunnerUtils.checkToolchainSupported(project, host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsCLionDebugRunnerUtils.checkToolchainConfigured(project)

    override fun processInvalidToolchain(project: Project, toolchainError: ToolchainError) {
        RsCLionDebugRunnerUtils.processInvalidToolchain(project, toolchainError)
    }
}
