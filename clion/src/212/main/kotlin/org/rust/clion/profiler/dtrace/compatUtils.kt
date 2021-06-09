/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.dtrace

import com.intellij.openapi.project.Project
import com.intellij.profiler.MisConfiguredException
import com.intellij.profiler.clion.CPPProfilerSettings
import com.intellij.profiler.clion.DTraceProfilerConfigurable
import com.intellij.profiler.validateFrequency
import com.intellij.profiler.validateLocalPath

@Throws(MisConfiguredException::class)
internal fun validateDTraceSettings(project: Project) {
    // TODO: Use DTraceProfilerSettings
    val state = CPPProfilerSettings.instance.state
    throw validateLocalPath(state.executablePath.orEmpty(), "DTrace executable", project, DTraceProfilerConfigurable::class.java)
        ?: validateFrequency(state.samplingFrequency, project, DTraceProfilerConfigurable::class.java)
        ?: return
}
