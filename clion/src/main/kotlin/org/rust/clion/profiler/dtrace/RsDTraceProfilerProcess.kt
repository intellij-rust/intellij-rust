/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.dtrace

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.profiler.DummyCallTreeBuilder
import com.intellij.profiler.api.*
import com.intellij.profiler.clion.dtrace.DTraceProfilerSettings
import com.intellij.profiler.dtrace.DTraceProfilerProcessBase
import com.intellij.profiler.dtrace.FullDumpParser
import com.intellij.profiler.dtrace.SimpleProfilerSettingsState
import com.intellij.profiler.dtrace.cpuProfilerScript
import com.intellij.profiler.model.NativeCall
import com.intellij.profiler.model.NativeThread
import com.intellij.profiler.sudo.SudoProcessHandler
import com.intellij.profiler.ui.NativeCallStackElementRenderer
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.concurrency.Promise
import org.rust.clion.profiler.RsCachingStackElementReader
import org.rust.lang.utils.RsDemangler


@Suppress("UnstableApiUsage")
class RsDTraceProfilerProcess private constructor(
    project: Project,
    targetProcess: AttachableTargetProcess,
    attachedTimestamp: Long,
    dtraceProcessHandler: SudoProcessHandler
) : DTraceProfilerProcessBase(project, targetProcess, attachedTimestamp, dtraceProcessHandler) {


    // We can't use [com.intellij.profiler.clion.ProfilerUtilsKt.CPP_PROFILER_HELP_TOPIC] directly
    // because it has `internal` modifier
    override val helpId: String = "procedures.profiler"

    override fun createDumpParser(): FullDumpParser<BaseCallStackElement> {
        val cachingStackElementReader = RsCachingStackElementReader.getInstance(project)
        // BACKCOMPAT: 2021.1. Since 2021.2, CLion does not show the thread id, so it should be specified as a thread name
        val shouldSpecifyThreadName = ApplicationInfo.getInstance().build >= BUILD_212
        return FullDumpParser(
            { if (shouldSpecifyThreadName) NativeThread(it, "thread with id $it") else NativeThread(it, "") },
            cachingStackElementReader::parseStackElement
        )
    }

    override fun createDumpWriter(data: NewCallTreeOnlyProfilerData): ProfilerDumpWriter =
        CollapsedProfilerDumpWriter(data.builder, targetProcess.fullName, attachedTimestamp, { it.fullName() }, { it.name })

    override fun createProfilerData(builder: DummyCallTreeBuilder<BaseCallStackElement>): NewCallTreeOnlyProfilerData =
        NewCallTreeOnlyProfilerData(builder, NativeCallStackElementRenderer.INSTANCE)

    override fun postProcessData(builder: DummyCallTreeBuilder<BaseCallStackElement>): DummyCallTreeBuilder<BaseCallStackElement> {
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
        return super.postProcessData(builder)
    }

    companion object {
        fun attach(
            targetProcess: AttachableTargetProcess,
            backgroundOption: PerformInBackgroundOption,
            timeoutInMilliseconds: Int,
            project: Project
        ): Promise<RsDTraceProfilerProcess> {
            val settings = DTraceProfilerSettings.instance.state

            // WARNING: Do not use such solution for other needs!
            // We want to always use -xmangled option because DTrace cannot demangle Rust symbols correctly
            val element = XmlSerializer.serialize(settings)
            val settingsCopy = XmlSerializer.deserialize(element, SimpleProfilerSettingsState::class.java)
            settingsCopy.defaultCmdArgs.add("-xmangled")

            return attachBase(
                targetProcess,
                backgroundOption,
                settingsCopy.cpuProfilerScript(),
                timeoutInMilliseconds,
                project
            ) { handler, _ -> RsDTraceProfilerProcess(project, targetProcess, System.currentTimeMillis(), handler) }
        }
    }
}

// BACKCOMPAT: 2021.1
private val BUILD_212: BuildNumber = BuildNumber.fromString("212")!!
