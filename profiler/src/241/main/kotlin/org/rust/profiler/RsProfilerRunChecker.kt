/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.util.SystemInfo
import com.intellij.profiler.clion.ProfilerRunChecker

class RsProfilerRunChecker : ProfilerRunChecker {
    override fun canRun(profile: RunProfile): Boolean = false
    override fun isDTraceProfilerCanBeUsed(): Boolean = SystemInfo.isMac
    override fun isPerfProfilerCanBeUsed(): Boolean = SystemInfo.isLinux || SystemInfo.isWindows
    override fun isProfilerCompatible(configuration: RunProfile): Boolean = true
}
