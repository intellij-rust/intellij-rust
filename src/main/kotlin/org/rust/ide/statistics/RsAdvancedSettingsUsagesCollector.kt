/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.options.advanced.AdvancedSettings
import org.rust.cargo.project.settings.MACROS_MAXIMUM_RECURSION_LIMIT_SETTING_KEY
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties.Companion.TEST_TOOL_WINDOW_SETTING_KEY
import org.rust.ide.docs.EXTERNAL_DOCUMENTATION_URL_SETTING_KEY
import org.rust.ide.typing.paste.CONVERT_JSON_TO_STRUCT_SETTING_KEY
import org.rust.ide.typing.paste.StoredPreference

@Suppress("UnstableApiUsage")
class RsAdvancedSettingsUsagesCollector : ApplicationUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(): Set<MetricEvent> {
        val metrics = mutableSetOf<MetricEvent>()
        metrics += TEST_TOOL_WINDOW.metric(AdvancedSettings.getBoolean(TEST_TOOL_WINDOW_SETTING_KEY))
        metrics += CONVERT_JSON_TO_STRUCT.metric(AdvancedSettings.getEnum(CONVERT_JSON_TO_STRUCT_SETTING_KEY, StoredPreference::class.java))
        val docUrlIsDocsRs = AdvancedSettings.getString(EXTERNAL_DOCUMENTATION_URL_SETTING_KEY) == "https://docs.rs/"
        metrics += EXTERNAL_DOCUMENTATION_URL.metric(docUrlIsDocsRs)
        metrics += MACROS_MAXIMUM_RECURSION_LIMIT.metric(AdvancedSettings.getInt(MACROS_MAXIMUM_RECURSION_LIMIT_SETTING_KEY))
        return metrics
    }

    companion object {
        private val GROUP = EventLogGroup("rust.advanced.settings", 1)

        private val TEST_TOOL_WINDOW = GROUP.registerEvent(
            "cargo_test_tool_window",
            EventFields.Boolean("enabled")
        )

        private val CONVERT_JSON_TO_STRUCT = GROUP.registerEvent(
            "convert_json_to_struct",
            EventFields.Enum<StoredPreference>("preference")
        )

        private val EXTERNAL_DOCUMENTATION_URL = GROUP.registerEvent(
            "external_doc_url",
            EventFields.Boolean("is_docs_rs")
        )


        private val MACROS_MAXIMUM_RECURSION_LIMIT = GROUP.registerEvent(
            "macros_maximum_recursion",
            EventFields.RoundedInt("limit")
        )
    }
}
