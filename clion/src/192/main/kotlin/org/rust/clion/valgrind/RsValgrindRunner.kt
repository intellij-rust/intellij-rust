/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.cpp.valgrind.ValgrindExecutor
import org.rust.cargo.runconfig.RsAsyncRunner

private const val ERROR_MESSAGE_TITLE: String = "Valgrind is not possible"

class RsValgrindRunner : RsAsyncRunner(ValgrindExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun isApplicable(): Boolean =
        SystemInfo.isMac || SystemInfo.isLinux

    companion object {
        const val RUNNER_ID: String = "RsValgrindRunner"
    }
}
