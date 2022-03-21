/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.rust.ide.newProject.RsCustomTemplate
import org.rust.ide.newProject.RsGenericTemplate
import org.rust.ide.newProject.RsProjectTemplate

@Suppress("UnstableApiUsage")
class RsCounterUsagesCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("rust.counters", 1)

        private val NEW_PROJECT_CREATION = GROUP.registerEvent("new_project_creation",
            EventFields.Enum<NewProjectTemplate>("template")
        )

        fun newProjectCreation(template: RsProjectTemplate) {
            NEW_PROJECT_CREATION.log(NewProjectTemplate.from(template))
        }
    }

    private enum class NewProjectTemplate {
        BINARY,
        LIBRARY,
        WASM,
        PROC_MACRO,
        CUSTOM;

        override fun toString(): String = name.lowercase()

        companion object {
            fun from(template: RsProjectTemplate): NewProjectTemplate {
                return when (template) {
                    RsGenericTemplate.CargoBinaryTemplate -> BINARY
                    RsGenericTemplate.CargoLibraryTemplate -> LIBRARY
                    RsCustomTemplate.WasmPackTemplate -> WASM
                    RsCustomTemplate.ProcMacroTemplate -> PROC_MACRO
                    is RsCustomTemplate -> CUSTOM
                }
            }
        }
    }
}
