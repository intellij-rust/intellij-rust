/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.openapi.project.Project
import com.intellij.profiler.clion.ProfilerEnvironmentHost
import com.intellij.profiler.clion.ProfilerEnvironmentHostProvider

class RsProfilerEnvironmentHostProvider: ProfilerEnvironmentHostProvider {
    override fun getEnvironmentHost(project: Project): ProfilerEnvironmentHost = RsProfilerEnvironmentHost()
}
