/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig.legacy

import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.BuildResult
import org.rust.debugger.runconfig.RsDebugRunnerUtils

class RsDebugRunnerLegacy : RsDebugRunnerLegacyBase() {

    override fun checkToolchainSupported(host: String): BuildResult.ToolchainError? =
        RsDebugRunnerUtils.checkToolchainSupported(host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsDebugRunnerUtils.checkToolchainConfigured(project)
}
