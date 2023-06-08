/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

@Suppress("UnstableApiUsage")
class CargoTestToolWindowUsagesCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("rust.cargo.test", 1)

        private val TEST_TOOL_WINDOW_DISABLED = GROUP.registerEvent("tool.window.disabled")

        fun logTestToolWindowDisabled() = TEST_TOOL_WINDOW_DISABLED.log()
    }
}
