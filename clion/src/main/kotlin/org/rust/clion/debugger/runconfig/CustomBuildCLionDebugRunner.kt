/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig

import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.BuildResult
import org.rust.debugger.runconfig.CustomBuildDebugRunnerBase

class CustomBuildCLionDebugRunner : CustomBuildDebugRunnerBase() {
    override fun checkToolchainSupported(project: Project, host: String): BuildResult.ToolchainError? =
        RsCLionDebugRunnerUtils.checkToolchainSupported(project, host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsCLionDebugRunnerUtils.checkToolchainConfigured(project)

    override fun processInvalidToolchain(project: Project, toolchainError: BuildResult.ToolchainError) {
        RsCLionDebugRunnerUtils.processInvalidToolchain(project, toolchainError)
    }
}
