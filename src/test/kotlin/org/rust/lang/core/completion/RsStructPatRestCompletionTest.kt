/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsStructPatRestCompletionTest : RsCompletionTestBase() {
    fun `test rest in let struct pat`() = checkContainsCompletion("..", """
        struct S { a: i32 }

        fn foo() {
            let S { /*caret*/ } = S { a: 0 };
        }
    """)

    fun `test rest in match struct pat`() = checkContainsCompletion("..", """
        struct S { a: i32 }

        fn foo(s: S) {
            match s {
                S { /*caret*/ } => {}
            }
        }
    """)

    fun `test do not offer rest if it's already present`() = checkNotContainsCompletion("..", """
        struct S { a: i32 }

        fn foo(s: S) {
            match s {
                S { a, /*caret*/, .. } => {}
            }
        }
    """)

    fun `test do not offer in struct constructor`() = checkNotContainsCompletion("..", """
        struct S { a: i32 }

        fn foo() {
            let s = S { /*caret*/ };
        }
    """)
}
