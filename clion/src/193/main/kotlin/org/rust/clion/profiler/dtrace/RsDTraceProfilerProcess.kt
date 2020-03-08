/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.dtrace

import com.intellij.openapi.project.Project
import com.intellij.profiler.api.AttachableTargetProcess
import com.intellij.profiler.api.ProfilerData
import com.intellij.profiler.api.ProfilerDumpWriter
import com.intellij.profiler.api.NewCallTreeOnlyProfilerData
import com.intellij.profiler.sudo.SudoProcessHandler

class RsDTraceProfilerProcess(
    project: Project,
    targetProcess: AttachableTargetProcess,
    attachedTimestamp: Long,
    dtraceProcessHandler: SudoProcessHandler
) : RsDTraceProfilerProcessBase(project, targetProcess, attachedTimestamp, dtraceProcessHandler) {

    override val dumpFileWriterFactory: ((ProfilerData) -> ProfilerDumpWriter?)? = { data: ProfilerData ->
        if (data is NewCallTreeOnlyProfilerData) createDumpWriterInner(data) else null
    }
}
