/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

@Suppress("UnstableApiUsage")
class CargoErrorCodesCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    companion object {

        private val GROUP = EventLogGroup("rust.cargo.build", 1)
        private val ERROR_CODE = EventFields.StringValidatedByInlineRegexp("error_code", "E0\\d{3}")

        private val ERROR_CODE_AFTER_BUILD = GROUP.registerEvent("error.emitted", ERROR_CODE)
        fun logErrorsAfterBuild(project: Project, errorCodes: Collection<String>) {
            errorCodes.forEach { ERROR_CODE_AFTER_BUILD.log(project, it) }
        }
    }
}
