/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig.legacy

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.rust.debugger.runconfig.RsDebugRunnerUtils

class RsDebugRunnerLegacy : RsDebugRunnerLegacyBase() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (!super.canRun(executorId, profile)) return false
        return SystemInfo.isMac || SystemInfo.isLinux
    }

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsDebugRunnerUtils.checkToolchainConfigured(project)
}
