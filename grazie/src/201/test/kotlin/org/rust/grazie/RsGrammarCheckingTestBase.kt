/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.PlatformTestUtil
import org.rust.ide.inspections.RsInspectionsTestBase

// BACKCOMPAT: 2019.3. Inline
abstract class RsGrammarCheckingTestBase : RsInspectionsTestBase(GrazieInspection::class) {

    override fun setUp() {
        super.setUp()
        val strategy = LanguageGrammarChecking.getStrategies().first { it is RsGrammarCheckingStrategy }
        val currentState = GrazieConfig.get()
        if (strategy.getID() !in currentState.enabledGrammarStrategies || currentState.enabledLanguages != enabledLanguages) {
            GrazieConfig.update { state ->
                state.copy(
                    enabledGrammarStrategies = state.enabledGrammarStrategies + strategy.getID(),
                    enabledLanguages = enabledLanguages
                )
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    companion object {
        val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    }
}
