/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.openapiext.BUILD_202

class RsDebugRunner : RsDebugRunnerBase() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (!super.canRun(executorId, profile)) return false
        if (ApplicationInfo.getInstance().build < BUILD_202) {
            if (!(SystemInfo.isMac || SystemInfo.isLinux)) return false
        }
        return true
    }

    override fun checkToolchainSupported(host: String): ToolchainError? =
        RsDebugRunnerUtils.checkToolchainSupported(host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsDebugRunnerUtils.checkToolchainConfigured(project)
}
