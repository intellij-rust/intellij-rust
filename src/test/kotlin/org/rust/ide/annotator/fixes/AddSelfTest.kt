/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase

class AddSelfTest : RsAnnotatorTestBase() {
    fun `test has no parameters`() = checkQuickFix("Add self to function", """
struct S;

impl S {
    fn foo() {
        self/*caret*/;
    }
}
""", """
struct S;

impl S {
    fn foo(&self) {
        self/*caret*/;
    }
}
""")

    fun `test has parameters`() = checkQuickFix("Add self to function", """
struct S;

impl S {
    fn foo(a: i32) {
        self/*caret*/;
    }
}
""", """
struct S;

impl S {
    fn foo(&self, a: i32) {
        self/*caret*/;
    }
}
""")
}
