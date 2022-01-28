/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.UiDslConfigurable
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.layout.RowBuilder
import org.rust.RsBundle

class RsAutoImportOptions : UiDslConfigurable.Simple(), AutoImportOptionsProvider {

    override fun RowBuilder.createComponentRow() {
        val settings = RsCodeInsightSettings.getInstance()
        titledRow(RsBundle.message("settings.rust.auto.import.title")) {
            row {
                checkBox(
                    RsBundle.message("settings.rust.auto.import.show.popup"),
                    settings::showImportPopup
                )
            }
            row {
                checkBox(
                    RsBundle.message("settings.rust.auto.import.on.completion"),
                    settings::importOutOfScopeItems
                )
            }
            row {
                cell {
                    checkBox(
                        ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"),
                        settings::addUnambiguousImportsOnTheFly
                    )
                    ContextHelpLabel.create(ApplicationBundle.message("help.add.unambiguous.imports"))()
                }
            }
        }
    }
}
