/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.settings

import com.intellij.application.options.GenerationCodeStylePanel
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.rust.lang.RsLanguage

class RsCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(RsLanguage, currentSettings, settings) {

    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
        addSpacesTab(settings)
        addWrappingAndBracesTab(settings)
        addBlankLinesTab(settings)
        addTab(GenerationCodeStylePanel(settings, RsLanguage))
    }
}
