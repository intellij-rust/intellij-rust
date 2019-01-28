/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction
import com.intellij.openapi.editor.ex.EditorEx
import org.rust.RsTestBase

abstract class RsQuickDefinitionTestBase : RsTestBase() {

    protected fun performShowImplementationAction(): String? {
        var actualText: String? = null

        val action = object : ShowImplementationsAction() {
            override fun showImplementations(session: ImplementationViewSession, invokedFromEditor: Boolean, invokedByShortcut: Boolean) {
                if (session.implementationElements.isEmpty()) return
                actualText = session.implementationElements[0].text
            }
        }

        action.performForContext((myFixture.editor as EditorEx).dataContext)
        return actualText
    }
}
