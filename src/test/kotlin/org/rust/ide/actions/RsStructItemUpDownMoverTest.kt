/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsStructItemUpDownMoverTest : RsBaseUpDownMoverTest() {

    fun `test step`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }

        """, """
            // - main.rs

            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over function multi liner`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            fn foo() {

            }
        """, """
            // - main.rs
            fn foo() {

            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over function`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            fn foo() {}
        """, """
            // - main.rs
            fn foo() {}
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over struct`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            struct S;
        """, """
            // - main.rs
            struct S;
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over struct fields`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            struct S {
                test: u32
            }
        """, """
            // - main.rs
            struct S {
                test: u32
            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over struct with outer attr`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[derive(Debug)]
            struct S;
        """, """
            // - main.rs
            #[derive(Debug)]
            struct S;
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over impl`() = moveBothDirectionTest("""
            struct S;
            struct /*caret*/ A{
                test: u32
            }
            impl S {}
        """, """
            struct S;
            impl S {}
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over trait with attr`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[test]
            trait S {}
        """, """
            // - main.rs
            #[test]
            trait S {}
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over trait`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            trait S {

            }
        """, """
            // - main.rs
            trait S {

            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over macro down`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            test!();
        """, """
            // - main.rs
            test!();
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over macro multiline down`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            test! {

            }
        """, """
            // - main.rs
            test! {

            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over macro rules`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            macro_rules! test {

            }
        """, """
            // - main.rs
            macro_rules! test {

            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over macro rules one line down`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            macro_rules! test {}
        """, """
            // - main.rs
            macro_rules! test {}
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over mod down`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            mod S {

            }
        """, """
            // - main.rs
            mod S {

            }
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over use`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            use test::{
                test
            };
        """, """
            // - main.rs
            use test::{
                test
            };
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over extern crate`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            extern crate test;
        """, """
            // - main.rs
            extern crate test;
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over extern crate with attr`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[macro_use]
            extern crate test;
        """, """
            // - main.rs
            #[macro_use]
            extern crate test;
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test step over inner attr`() = moveBothDirectionTest("""
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #![allow(bad_style)]
        """, """
            // - main.rs
            #![allow(bad_style)]
            struct /*caret*/ A{
                test: u32
            }
        """)

    fun `test mod prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            mod s {
                struct /*caret*/ A{
                    test: u32
                }
            }
        """, """
            // - main.rs
            mod s {
                struct /*caret*/ A{
                    test: u32
                }
            }
        """)

    fun `test function prevent step out`() = moveBothDirectionTest("""
            // - main.rs
            fn s() {
                struct /*caret*/ A{
                    test: u32
                }
            }
        """, """
            // - main.rs
            fn s() {
                struct /*caret*/ A{
                    test: u32
                }
            }
        """)
}
