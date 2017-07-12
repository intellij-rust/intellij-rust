/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsStructItemMoverTest: RsTestBase() {

    fun `test step up`() = moveUpTest("""
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

    fun `test step selection up`() = moveUpTest("""
            // - main.rs

            fn <selection>test() {
            </selection>
            }
        """, """
            // - main.rs
            fn test() {

            }

        """)

    fun `test step down`() = moveDownTest("""
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

    fun `test step over function up`() = moveUpTest("""
            // - main.rs
            fn foo() {}
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            fn foo() {}
        """)

    fun `test step over function multi liner up`() = moveUpTest("""
            // - main.rs
            fn foo() {

            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            fn foo() {

            }
        """)

    fun `test step over function down`() = moveDownTest("""
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

    fun `test step over struct up`() = moveUpTest("""
            // - main.rs
            struct S;
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            struct S;
        """)

    fun `test step over struct down`() = moveDownTest("""
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

    fun `test step over struct fields up`() = moveUpTest("""
            // - main.rs
            struct S {
                test: u32
            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            struct S {
                test: u32
            }
        """)

    fun `test step over struct fields down`() = moveDownTest("""
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

    fun `test step over struct with outer attr up`() = moveUpTest("""
            // - main.rs
            #[derive(Debug)]
            struct S;
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[derive(Debug)]
            struct S;
        """)

    fun `test step over struct with outer attr down`() = moveDownTest("""
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

    fun `test step over impl up`() = moveUpTest("""
            struct S;
            impl S {}
            struct /*caret*/ A{
                test: u32
            }
        """, """
            struct S;
            struct /*caret*/ A{
                test: u32
            }
            impl S {}
        """)

    fun `test step over impl down`() = moveDownTest("""
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

    fun `test step over trait with attr up`() = moveUpTest("""
            // - main.rs
            #[test]
            trait S {}
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[test]
            trait S {}
        """)

    fun `test step over trait with attr down`() = moveDownTest("""
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

    fun `test step over trait up`() = moveUpTest("""
            // - main.rs
            trait S {

            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            trait S {

            }
        """)

    fun `test step over trait down`() = moveDownTest("""
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

    fun `test step over macro up`() = moveUpTest("""
            // - main.rs
            test!();
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            test!();
        """)

    fun `test step over macro down`() = moveDownTest("""
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

    fun `test step over macro multiline up`() = moveUpTest("""
            // - main.rs
            test!{

            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            test!{

            }
        """)

    fun `test step over macro multiline down`() = moveDownTest("""
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

    fun `test step over macro rules up`() = moveUpTest("""
            // - main.rs
            macro_rules! test {

            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            macro_rules! test {

            }
        """)

    fun `test step over macro rules down`() = moveDownTest("""
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

    fun `test step over macro rules one line up`() = moveUpTest("""
            // - main.rs
            macro_rules! test {}
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            macro_rules! test {}
        """)

    fun `test step over macro rules one line down`() = moveDownTest("""
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

    fun `test step over mod up`() = moveUpTest("""
            // - main.rs
            mod S {

            }
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            mod S {

            }
        """)

    fun `test step over mod down`() = moveDownTest("""
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

    fun `test step over use up`() = moveUpTest("""
            // - main.rs
            use test::{
                test
            };
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            use test::{
                test
            };
        """)

    fun `test step over use down`() = moveDownTest("""
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

    fun `test step over extern crate up`() = moveUpTest("""
            // - main.rs
            extern crate test;
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            extern crate test;
        """)

    fun `test step over extern crate down`() = moveDownTest("""
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

    fun `test step over extern crate with attr up`() = moveUpTest("""
            // - main.rs
            #[macro_use]
            extern crate test;
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #[macro_use]
            extern crate test;
        """)

    fun `test step over extern crate with attr down`() = moveDownTest("""
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

    fun `test step over inner attr up`() = moveUpTest("""
            // - main.rs
            #![allow(bad_style)]
            struct /*caret*/ A{
                test: u32
            }
        """, """
            // - main.rs
            struct /*caret*/ A{
                test: u32
            }
            #![allow(bad_style)]
        """)

    fun `test step over inner attr down`() = moveDownTest("""
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

    fun `test mod prevent step out up`() = moveUpTest("""
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

    fun `test mod prevent step out down`() = moveDownTest("""
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

    fun `test function prevent step out up`() = moveUpTest("""
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

    fun `test function prevent step out down`() = moveDownTest("""
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

    fun moveUpTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkByText(before, after) {
            myFixture.performEditorAction(ACTION_MOVE_STATEMENT_UP_ACTION)
        }
    }

    fun moveDownTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkByText(before, after) {
            myFixture.performEditorAction(ACTION_MOVE_STATEMENT_DOWN_ACTION)
        }
    }
}
