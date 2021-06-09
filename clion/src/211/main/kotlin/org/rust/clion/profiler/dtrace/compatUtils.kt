/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.dtrace

import com.intellij.openapi.project.Project
import com.intellij.profiler.MisConfiguredException
import com.intellij.profiler.clion.CPPProfilerSettings
import com.intellij.profiler.clion.profilerConfigurable
import com.intellij.profiler.validateFrequency
import com.intellij.profiler.validateLocalPath

@Throws(MisConfiguredException::class)
internal fun validateDTraceSettings(project: Project) {
    val state = CPPProfilerSettings.instance.state
    throw validateLocalPath(state.executablePath.orEmpty(), "DTrace executable", project, profilerConfigurable::class.java)
        ?: validateFrequency(state.samplingFrequency, project, profilerConfigurable::class.java)
        ?: return
}
