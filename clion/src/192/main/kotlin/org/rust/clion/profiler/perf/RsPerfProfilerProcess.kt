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
import com.intellij.profiler.clion.perf.CachedPerfFlameChartBuilder
import com.intellij.profiler.clion.perf.PerfScriptProcess
import com.intellij.profiler.ui.flamechart.NativeCallChartNodeRenderer
import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.RsFunction

class RsPerfProfilerProcess(
    perfProcessHandler: BaseProcessHandler<*>,
    private val outputPerfDataPath: String,
    private val addressSymbolizer: AddressSymbolizer?,
    processName: String,
    project: Project,
    override val attachedTimestamp: Long,
    private val navigatableClass: Class<out NavigatablePsiElement> = RsFunction::class.java
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
                    val builder = CachedPerfFlameChartBuilder(10000, addressSymbolizer, readIndicator, navigatableClass)
                    changeStateAndNotifyAsync(ReadingData)
                    PerfScriptProcess.execute(outputPerfDataPath, { builder.add(it) }, readIndicator)
                    val data = CallTreeOnlyProfilerData(builder.getFlameChartBuilder(), NativeCallChartNodeRenderer.INSTANCE)
                    //TODO: see com.intellij.profiler.ultimate.async.AsyncProfilerProcessBase.readDumpFrom
                    readIndicator.checkCanceled()
                    changeStateAndNotifyAsync(DataReady(data))
                } catch (e: Throwable) { //should i really handle errors this way?
                    if (e !is ProcessCanceledException) {
                        changeStateAndNotifyAsync(ProfilerError(e.message.orEmpty()))
                    }
                }
            }, readIndicator)
        }
    }

    override val dumpFileWriterFactory: ((ProfilerData) -> ProfilerDumpWriter?)? = null

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
