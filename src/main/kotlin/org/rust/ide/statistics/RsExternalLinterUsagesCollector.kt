/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.RoundedLong
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

@Suppress("UnstableApiUsage")
class RsExternalLinterUsagesCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("rust.external.linter", 1)

        private val ON_THE_FLY_EXECUTION_TIME = GROUP.registerEvent("on.the.fly.execution", RoundedLong("duration_ms"))

        fun logOnTheFlyExecutionTime(durationMs: Long) {
            ON_THE_FLY_EXECUTION_TIME.log(durationMs)
        }
    }
}
