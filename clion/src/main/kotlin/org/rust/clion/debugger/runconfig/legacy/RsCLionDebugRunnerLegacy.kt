/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger.runconfig.legacy

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.concurrency.AsyncPromise
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.clion.debugger.runconfig.RsCLionDebugRunnerUtils
import org.rust.debugger.runconfig.RsDebugRunnerUtils.MSVC_IS_NOT_SUPPORTED_MESSAGE
import org.rust.debugger.runconfig.legacy.RsDebugRunnerLegacyBase

class RsCLionDebugRunnerLegacy : RsDebugRunnerLegacyBase() {
    override fun checkToolchainConfigured(project: Project): Boolean =
        RsCLionDebugRunnerUtils.checkToolchainConfigured(project)
}
