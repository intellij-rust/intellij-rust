/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.hasCaretMarker
import org.rust.launchAction

abstract class RsInlineTestBase : RsTestBase() {

    protected fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        check(hasCaretMarker(before))
        checkByText(before.trimIndent(), after.trimIndent()) {
            myFixture.performEditorAction("Inline")
        }
    }

    protected fun doUnavailableTest(@Language("Rust") code: String) {
        InlineFile(code.trimIndent()).withCaret()
        myFixture.launchAction("Inline", shouldBeEnabled = false)
    }

    protected inline fun <reified T : Throwable> expectError(code: String) {
        check(hasCaretMarker(code))
        InlineFile(code)
        expect<T> { myFixture.performEditorAction("Inline") }
    }
}
