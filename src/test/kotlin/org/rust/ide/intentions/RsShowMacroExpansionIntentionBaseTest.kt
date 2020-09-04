/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.ide.actions.macroExpansion.MacroExpansionViewDetails

class RsShowMacroExpansionIntentionBaseTest : RsIntentionTestBase(RsShowMacroExpansionIntention::class) {

    override fun setUp() {
        super.setUp()
        IntentionManager.getInstance().addAction(RsShowMacroExpansionIntention)
    }

    override fun tearDown() {
        IntentionManager.getInstance().unregisterIntention(RsShowMacroExpansionIntention)
        super.tearDown()
    }

    fun `test that intention is not available outside of the macros`() = doUnavailableTest("""
        foo!();
        /*caret*/foo();
    """)

    fun `test that intention is available on the macros, but does not change it`() = doAvailableTest("""
        /*caret*/foo!();
        foo();
    """, """
        foo!();
        foo();
    """)
}

object RsShowMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = true) {
    override fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
        // do not show anything
    }
}
