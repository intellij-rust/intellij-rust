/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.*

abstract class RsQualifiedNameProviderTestBase : RsTestBase() {
    protected fun doTest(
        @Language("Rust") code: String,
        expected : String
    ) {
        InlineFile(code)

        val element = findElementInEditor<RsReferenceElement>()
        assertEquals(expected, CopyReferenceAction.elementToFqn(element.reference.resolve()))
    }
}
