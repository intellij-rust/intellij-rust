/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.refactoring.RsConvertToNamedFieldsAction

class RsConvertToNamedFieldsActionTest : RsTestBase() {
    fun `test works for structs`() = doTest("""
        struct/*caret*/ S(i32, pub String);
    """, """
        struct S {
            _0: i32,
            pub _1: String
        }
    """)

    fun `test works for an enum variant`() = doTest("""
        enum E { S1(i32, /*caret*/String), S3 }
    """, """
        enum E { S1 {
            _0: i32,
            _1: String
        }, S3 }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before).withCaret()
        myFixture.testAction(RsConvertToNamedFieldsAction())
        myFixture.checkResult(after)
    }
}
