/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.expansion

class RsMacroErrorTestBase : RsTestBase() {
    private fun checkNotExpanded(@Language("Rust") code: String) {
        InlineFile(code)
        val calls = myFixture.file.descendantsOfType<RsMacroCall>()
        check(calls.all { it.expansion == null })
    }

    // https://github.com/intellij-rust/intellij-rust/pull/2583
    fun `test empty group definition`() = checkNotExpanded("""
        macro_rules! foo {
            ($()* $ i:tt) => {  }
        }
        foo! { bar baz }
    """)
}
