/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension

@Suppress("UnstableApiUsage")
class CargoCommandUsagesCollector: FeatureUsageCollectorExtension {
    override fun getGroupId(): String = RunConfigurationUsageTriggerCollector.GROUP_NAME

    override fun getEventId(): String = "started"

    override fun getExtensionFields(): List<StringEventField> = listOf(COMMAND)

    companion object {
        val COMMAND = EventFields.String("command", listOf("run", "test", "build"))
    }
}
