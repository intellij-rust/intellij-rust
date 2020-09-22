/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.execution.process.BaseProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.profiler.clion.perf.Addr2LineProcess
import com.intellij.profiler.clion.perf.AddressSymbolizer
import com.intellij.profiler.clion.perf.PerfProfilerProcess
import org.rust.lang.core.psi.RsFunction
import java.io.File

fun createPerfProfilerProcess(
    handler: BaseProcessHandler<*>,
    outputFile: File,
    processName: String,
    project: Project
) : PerfProfilerProcess {
    return PerfProfilerProcess(
        handler,
        outputFile,
        createAddressSymbolizer(),
        processName,
        project,
        System.currentTimeMillis(),
        RsFunction::class.java
    )
}

private fun createAddressSymbolizer(): AddressSymbolizer? {
    return if (Addr2LineProcess.isAvailable) {
        Addr2LineProcess()
    } else {
        null
    }
}
