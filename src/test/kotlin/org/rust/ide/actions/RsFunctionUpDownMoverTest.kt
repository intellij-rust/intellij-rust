/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsFunctionUpDownMoverTest : RsBaseUpDownMoverTest() {

    fun `test step selection`() = moveBothDirectionTest("""
            // - main.rs
            fn <selection>test() {</selection>

            }

        """, """
            // - main.rs

            fn <selection>test() {</selection>

            }
        """)

    fun `test step`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }

        """, """
            // - main.rs

            fn /*caret*/test() {

            }
        """)

    fun `test step over function multi liner`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            fn foo() {

            }
        """, """
            // - main.rs
            fn foo() {

            }
            fn /*caret*/test() {

            }
        """)

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

    fun `test step over function`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            fn foo() {}
        """, """
            // - main.rs
            fn foo() {}
            fn /*caret*/test() {

            }
        """)

    fun `test step over struct`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            struct S;
        """, """
            // - main.rs
            struct S;
            fn /*caret*/test() {

            }
        """)

    fun `test step over struct fields`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            struct S {
                test: u32
            }
        """, """
            // - main.rs
            struct S {
                test: u32
            }
            fn /*caret*/test() {

            }
        """)

    fun `test step over struct with outer attr`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[derive(Debug)]
            struct S;
        """, """
            // - main.rs
            #[derive(Debug)]
            struct S;
            fn /*caret*/test() {

            }
        """)

    fun `test step over impl`() = moveBothDirectionTest("""
            struct S;
            fn /*caret*/test() {

            }
            impl S {}
        """, """
            struct S;
            impl S {}
            fn /*caret*/test() {

            }
        """)

    fun `test step over trait with attr`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[test]
            trait S {}
        """, """
            // - main.rs
            #[test]
            trait S {}
            fn /*caret*/test() {

            }
        """)

    fun `test step over trait`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            trait S {

            }
        """, """
            // - main.rs
            trait S {

            }
            fn /*caret*/test() {

            }
        """)

    fun `test step over macro`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            test!();
        """, """
            // - main.rs
            test!();
            fn /*caret*/test() {

            }
        """)

    fun `test step over macro multiline`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            test! {

            }
        """, """
            // - main.rs
            test! {

            }
            fn /*caret*/test() {

            }
        """)

    fun `test step over macro rules`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            macro_rules! test {

            }
        """, """
            // - main.rs
            macro_rules! test {

            }
            fn /*caret*/test() {

            }
        """)

    fun `test step over macro rules one line`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            macro_rules! test {}
        """, """
            // - main.rs
            macro_rules! test {}
            fn /*caret*/test() {

            }
        """)

    fun `test step over mod`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            mod S {

            }
        """, """
            // - main.rs
            mod S {

            }
            fn /*caret*/test() {

            }
        """)

    fun `test step over use`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            use test::{
                test
            };
        """, """
            // - main.rs
            use test::{
                test
            };
            fn /*caret*/test() {

            }
        """)

    fun `test step over extern crate`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            extern crate test;
        """, """
            // - main.rs
            extern crate test;
            fn /*caret*/test() {

            }
        """)

    fun `test step over extern crate with attr`() = moveBothDirectionTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[macro_use]
            extern crate test;
        """, """
            // - main.rs
            #[macro_use]
            extern crate test;
            fn /*caret*/test() {

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

    fun `test mod prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            mod s {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            mod s {
                fn /*caret*/test() {
                    test!();
                }
            }
        """)

    fun `test function prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            fn s() {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            fn s() {
                fn /*caret*/test() {
                    test!();
                }
            }
        """)
}
