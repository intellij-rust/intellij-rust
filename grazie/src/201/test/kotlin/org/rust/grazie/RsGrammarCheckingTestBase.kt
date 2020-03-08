/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.testFramework.PlatformTestUtil
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.lang.RsLanguage

// BACKCOMPAT: 2019.3. Inline
abstract class RsGrammarCheckingTestBase : RsInspectionsTestBase(GrazieInspection::class) {

    // TODO: enable this tests after issue with resources will be fixed
    override fun shouldRunTest(): Boolean {
        return false
    }

    override fun setUp() {
        super.setUp()
        if (RsLanguage.id !in GrazieConfig.get().enabledProgrammingLanguages) {
            GrazieConfig.update { state ->
                state.copy(enabledProgrammingLanguages = state.enabledProgrammingLanguages + RsLanguage.id)
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }
}
