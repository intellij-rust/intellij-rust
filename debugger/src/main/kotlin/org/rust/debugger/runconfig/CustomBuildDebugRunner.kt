/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.BuildResult
import org.rust.cargo.runconfig.customBuild.CustomBuildConfiguration
import org.rust.cargo.toolchain.wsl.RsWslToolchain

class CustomBuildDebugRunner : CustomBuildDebugRunnerBase() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        super.canRun(executorId, profile) &&
            profile is CustomBuildConfiguration &&
            profile.project.toolchain !is RsWslToolchain

    override fun checkToolchainSupported(project: Project, host: String): BuildResult.ToolchainError? =
        RsDebugRunnerUtils.checkToolchainSupported(project, host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsDebugRunnerUtils.checkToolchainConfigured(project)
}
