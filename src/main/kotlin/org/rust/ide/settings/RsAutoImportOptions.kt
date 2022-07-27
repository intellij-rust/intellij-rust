/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import org.rust.RsBundle
import org.rust.openapiext.fullWidthCell

class RsAutoImportOptions(project: Project) : UiDslUnnamedConfigurable.Simple(), AutoImportOptionsProvider {

    private val excludeTable: RsPathsExcludeTable = RsPathsExcludeTable(project)

    override fun Panel.createContent() {
        val settings = RsCodeInsightSettings.getInstance()
        group(RsBundle.message("settings.rust.auto.import.title")) {
            row {
                checkBox(RsBundle.message("settings.rust.auto.import.show.popup"))
                    .bindSelected(settings::showImportPopup)
            }
            row {
                checkBox(RsBundle.message("settings.rust.auto.import.on.completion"))
                    .bindSelected(settings::importOutOfScopeItems)
            }
            row {
                checkBox(RsBundle.message("settings.rust.auto.import.on.paste"))
                    .bindSelected(settings::importOnPaste)
            }
            row {
                checkBox(ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"))
                    .bindSelected(settings::addUnambiguousImportsOnTheFly)
                    .gap(RightGap.SMALL)
                contextHelp(ApplicationBundle.message("help.add.unambiguous.imports"))
            }
            row {
                fullWidthCell(excludeTable.component)
                    .label(RsBundle.message("settings.rust.auto.import.exclude.label"), LabelPosition.TOP)
                    .comment(RsBundle.message("settings.rust.auto.import.exclude.comment"), maxLineLength = 100)
                    .onApply { excludeTable.apply() }
                    .onReset { excludeTable.reset() }
                    .onIsModified { excludeTable.isModified() }
            }
        }
    }
}
