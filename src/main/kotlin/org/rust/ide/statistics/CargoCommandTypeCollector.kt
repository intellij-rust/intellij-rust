/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import org.rust.ide.statistics.CargoCommandUsagesCollector.Companion.COMMAND

@Suppress("UnstableApiUsage")
class CargoCommandTypeCollector: FeatureUsageCollectorExtension {
    override fun getGroupId(): String = RunConfigurationTypeUsagesCollector.GROUP.id

    override fun getEventId(): String = RunConfigurationTypeUsagesCollector.CONFIGURED_IN_PROJECT

    override fun getExtensionFields(): List<StringEventField> = listOf(COMMAND)

}
