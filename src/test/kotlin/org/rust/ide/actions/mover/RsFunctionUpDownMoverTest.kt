/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

class RsFunctionUpDownMoverTest : RsStatementUpDownMoverTestBase() {

    // This test fails in run intellij
    fun `test don't step over function multi liner`() = moveBothDirectionTest("""
// - main.rs
fn /*caret*/test() {
    unimplemented!()
}

fn foo() {
    unimplemented!()
}

        """, """
// - main.rs

fn /*caret*/test() {
    unimplemented!()
}
fn foo() {
    unimplemented!()
}

        """)

    fun `test step over function multi liner in impl`() = moveBothDirectionTest("""
            // - main.rs
            struct S;
            impl s {
                fn /*caret*/test() {

                }
                fn foo() {

                }
            }
        """, """
            // - main.rs
            struct S;
            impl s {
                fn foo() {

                }
                fn /*caret*/test() {

                }
            }
        """)

    fun `test step over inner attr`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #![allow(bad_style)]
        """, """
            // - main.rs
            #![allow(bad_style)]
            fn /*caret*/test() {

            }
        """)

    fun `test impl prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            struct S;
            impl S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            struct S;
            impl S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """)

    fun `test trait prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            trait S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            trait S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """)
}
