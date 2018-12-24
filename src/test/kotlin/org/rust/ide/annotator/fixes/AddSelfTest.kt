/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddSelfTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {
    fun `test has no parameters`() = checkFixByText("Add self to function", """
        struct S;

        impl S {
            fn foo() {
                <error>self/*caret*/</error>;
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

    fun `test has parameters`() = checkFixByText("Add self to function", """
        struct S;

        impl S {
            fn foo(a: i32) {
                <error>self/*caret*/</error>;
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
