/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.apache.commons.lang3.ObjectUtils
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import kotlin.system.measureTimeMillis

abstract class RsPerfTestBase : RsRealProjectTestBase() {

    protected fun openProject(info: RealProjectInfo) {
        project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        openRealProject(info) ?: error("Can't open project")
    }
}

fun profile(label: String, warmupIterations: Int = 10, action: () -> Unit) {
    val times = mutableListOf<Long>()
    for (i in 0..Int.MAX_VALUE) {
        val time = measureTimeMillis {
            action()
        }

        val iteration = "#${i + 1}".padStart(5)
        val statistics = if (i < warmupIterations) {
            "warmup"
        } else {
            times += time
            val timeMin = times.minOrNull()
            val timeMedian = ObjectUtils.median(*times.toTypedArray())
            "min = $timeMin ms, median = $timeMedian ms"
        }
        val timePadded = "$time".padStart(5)
        println("$iteration: $label in $timePadded ms  ($statistics)")
    }
}
