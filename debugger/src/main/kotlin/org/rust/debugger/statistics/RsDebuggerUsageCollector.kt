/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

@Suppress("UnstableApiUsage")
class RsDebuggerUsageCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return GROUP
    }

    companion object {
        private val GROUP = EventLogGroup("rust.debug.evaluate.expression", 1)

        private val EXPRESSION_EVALUATED = GROUP.registerEvent("evaluated", EventFields.Boolean("success"))

        fun logEvaluated(success: Boolean) {
            EXPRESSION_EVALUATED.log(success)
        }
    }
}
