/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.profiler.ProfilerProcessBase
import com.intellij.profiler.api.*
import com.intellij.profiler.clion.NativeTargetProcess
import com.intellij.profiler.clion.perf.AddressSymbolizer
import com.intellij.profiler.clion.perf.PerfScriptProcess
import com.intellij.profiler.ui.flamechart.NativeCallChartNodeRenderer
import org.rust.clion.profiler.RsCachedPerfFlameChartBuilder

class RsPerfProfilerProcess(
    perfProcessHandler: BaseProcessHandler<*>,
    private val outputPerfDataPath: String,
    private val addressSymbolizer: AddressSymbolizer?,
    processName: String,
    project: Project,
    override val attachedTimestamp: Long
) : ProfilerProcessBase<NativeTargetProcess>(
    project,
    NativeTargetProcess(OSProcessUtil.getProcessID(perfProcessHandler.process), processName)
) {
    init {
        initAlreadyAttached(perfProcessHandler)
    }

    override fun onTargetProcessTerminated() {
        if (state !== Attached) return
        ApplicationManager.getApplication().executeOnPooledThread {
            if (state !== Attached) return@executeOnPooledThread
            ProgressManager.getInstance().runProcess({
                try {
                    val builder = RsCachedPerfFlameChartBuilder(10000, addressSymbolizer, readIndicator)
                    changeStateAndNotifyAsync(ReadingData)
                    PerfScriptProcess.execute(outputPerfDataPath, builder::add, readIndicator)
                    val data = ProfilerData(builder.getFlameChartBuilder(), NativeCallChartNodeRenderer.factory, null)
                    readIndicator.checkCanceled()
                    profilerData = data
                    changeStateAndNotifyAsync(DataReady(data))
                } catch (e: Throwable) {
                    if (e !is ProcessCanceledException) {
                        changeStateAndNotifyAsync(ProfilerError(e.message.orEmpty()))
                    }
                }
            }, readIndicator)
        }
    }

    override fun canBeStopped(): Boolean = false

    override fun stop(): Boolean {
        LOG.warn("Perf profiler process can't be stopped yet")
        return false
    }

    override val LOG = staticLogger

    companion object {
        private val staticLogger = Logger.getInstance(RsPerfProfilerProcess::class.java)
    }
}
