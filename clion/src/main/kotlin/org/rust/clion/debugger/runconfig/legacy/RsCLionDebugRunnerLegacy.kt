/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig.legacy

import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.cargo.runconfig.legacy.RsAsyncRunner.Companion.Binary
import org.rust.clion.debugger.runconfig.RsCLionDebugRunnerUtils
import org.rust.debugger.runconfig.legacy.RsDebugRunnerLegacyBase

class RsCLionDebugRunnerLegacy : RsDebugRunnerLegacyBase() {

    override fun checkToolchainSupported(project: Project, host: String): ToolchainError? =
        RsCLionDebugRunnerUtils.checkToolchainSupported(project, host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsCLionDebugRunnerUtils.checkToolchainConfigured(project)

    override fun processUnsupportedToolchain(
        project: Project,
        toolchainError: ToolchainError,
        promise: AsyncPromise<Binary?>
    ) {
        RsCLionDebugRunnerUtils.processInvalidToolchain(project, toolchainError)
        promise.setResult(null)
    }
}
