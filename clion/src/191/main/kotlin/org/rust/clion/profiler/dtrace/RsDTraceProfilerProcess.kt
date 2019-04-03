/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.dtrace

import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.project.Project
import com.intellij.profiler.DummyFlameChartBuilder
import com.intellij.profiler.api.AttachableTargetProcess
import com.intellij.profiler.api.BaseCallStackElement
import com.intellij.profiler.api.CollapsedProfilerDumpWriter
import com.intellij.profiler.api.ProfilerData
import com.intellij.profiler.clion.CPPProfilerSettings
import com.intellij.profiler.dtrace.DTraceProfilerProcessBase
import com.intellij.profiler.dtrace.FullDumpParser
import com.intellij.profiler.dtrace.SimpleProfilerSettingsState
import com.intellij.profiler.dtrace.cpuProfilerScript
import com.intellij.profiler.model.NativeCall
import com.intellij.profiler.model.NativeThread
import com.intellij.profiler.model.ThreadInfo
import com.intellij.profiler.sudo.SudoProcessHandler
import com.intellij.profiler.ui.flamechart.NativeCallChartNodeRenderer
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.concurrency.Promise
import org.rust.clion.profiler.RsCachingStackElementReader
import org.rust.lang.utils.RsDemangler


class RsDTraceProfilerProcess private constructor(
    project: Project,
    targetProcess: AttachableTargetProcess,
    attachedTimestamp: Long,
    dtraceProcessHandler: SudoProcessHandler
) : DTraceProfilerProcessBase(project, targetProcess, attachedTimestamp, dtraceProcessHandler) {

    override fun createDumpParser(): FullDumpParser<ThreadInfo, BaseCallStackElement> {
        val cachingStackElementReader = RsCachingStackElementReader.getInstance(project)
        return FullDumpParser(NativeThread.Companion::fromId, cachingStackElementReader::parseStackElement)
    }

    override fun createProfilerData(builder: DummyFlameChartBuilder<ThreadInfo, BaseCallStackElement>) =
        ProfilerData(
            builder, NativeCallChartNodeRenderer.factory, null,
            CollapsedProfilerDumpWriter(builder, targetProcess.fullName, attachedTimestamp, { it.fullName() }, { it.name })
        )

    override fun postProcessData(
        builder: DummyFlameChartBuilder<ThreadInfo, BaseCallStackElement>
    ): DummyFlameChartBuilder<ThreadInfo, BaseCallStackElement> =
        builder.mapTreeElements {
            if (it !is RsDTraceNavigatableNativeCall) return@mapTreeElements it
            val library = it.fullName().substringBeforeLast('`')
            val fullName = it.fullName().substringAfterLast('`')
            val demangledName = RsDemangler.tryDemangle(fullName)?.format(skipHash = true) ?: return@mapTreeElements it
            val path = demangledName.substringBeforeLast("::")
            val method = demangledName.substringAfterLast("::")
            val nativeCall = NativeCall(library, path, method)
            RsDTraceNavigatableNativeCall(nativeCall)
        }

    companion object {
        fun attach(
            targetProcess: AttachableTargetProcess,
            backgroundOption: PerformInBackgroundOption,
            timeoutInMilliseconds: Int,
            project: Project
        ): Promise<RsDTraceProfilerProcess> {
            val settings = CPPProfilerSettings.instance.state

            // WARNING: Do not use such solution for other needs!
            // We want to always use -xmangled option because DTrace cannot demangle Rust symbols correctly
            val element = XmlSerializer.serialize(settings)
            val settingsCopy = XmlSerializer.deserialize(element, SimpleProfilerSettingsState::class.java)
            settingsCopy.defaultCmdArgs.add("-xmangled")

            return DTraceProfilerProcessBase.attachBase(
                targetProcess,
                backgroundOption,
                settingsCopy.cpuProfilerScript(),
                timeoutInMilliseconds,
                project
            ) { handler, _ -> RsDTraceProfilerProcess(project, targetProcess, System.currentTimeMillis(), handler) }
        }
    }
}
