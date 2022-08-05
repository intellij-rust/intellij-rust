/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.hasCaretMarker

abstract class RsInlineFunctionTestBase : RsTestBase() {

    protected fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        check(hasCaretMarker(before))
        checkByText(before.trimIndent(), after.trimIndent()) {
            myFixture.performEditorAction("Inline")
        }
    }

    protected inline fun <reified T : Throwable> expectError(code: String) {
        check(hasCaretMarker(code))
        InlineFile(code)
        expect<T> { myFixture.performEditorAction("Inline") }
    }
}
