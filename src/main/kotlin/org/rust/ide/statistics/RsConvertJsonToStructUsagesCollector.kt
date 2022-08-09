/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

@Suppress("UnstableApiUsage")
class RsConvertJsonToStructUsagesCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("rust.generate.type.from.json.usage", 1)

        private val JSON_LIKE_TEXT_PASTED = GROUP.registerEvent("json.like.text.pasted")
        private val PASTED_JSON_CONVERTED = GROUP.registerEvent("pasted.json.converted")

        private val REMEMBER_CHOICE_RESULT = GROUP.registerEvent(
            "json.paste.dialog.remember.choice.result",
            EventFields.Boolean("result")
        )

        fun logJsonTextPasted() {
            JSON_LIKE_TEXT_PASTED.log()
        }

        fun logPastedJsonConverted() {
            PASTED_JSON_CONVERTED.log()
        }

        fun logRememberChoiceResult(result: Boolean) {
            REMEMBER_CHOICE_RESULT.log(result)
        }
    }
}
