/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsFunctionMoverTest : RsTestBase() {

    fun `test step up`() = moveUpTest("""
            // - main.rs

            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

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
            fn /*caret*/test() {

            }

        """, """
            // - main.rs

            fn test() {

            }
        """)

    fun `test step over function up`() = moveUpTest("""
            // - main.rs
            fn foo() {}
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            fn foo() {}
        """)

    fun `test step over function multi liner up`() = moveUpTest("""
            // - main.rs
            fn foo() {

            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            fn foo() {

            }
        """)

    fun `test step over function multi liner in impl up`() = moveUpTest("""
            // - main.rs
            struct S;
            impl s {
                fn foo() {

                }
                fn /*caret*/test() {

                }
            }
        """, """
            // - main.rs
            struct S;
            impl s {
                fn test() {

                }
                fn foo() {

                }
            }
        """)

    fun `test step over function multi liner in impl down`() = moveDownTest("""
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
                fn test() {

                }
            }
        """)

    fun `test step over function down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            fn foo() {}
        """, """
            // - main.rs
            fn foo() {}
            fn test() {

            }
        """)

    fun `test step over struct up`() = moveUpTest("""
            // - main.rs
            struct S;
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            struct S;
        """)

    fun `test step over struct down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            struct S;
        """, """
            // - main.rs
            struct S;
            fn test() {

            }
        """)

    fun `test step over struct fields up`() = moveUpTest("""
            // - main.rs
            struct S {
                test: u32
            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            struct S {
                test: u32
            }
        """)

    fun `test step over struct fields down`() = moveDownTest("""
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
            fn test() {

            }
        """)

    fun `test step over struct with outer attr up`() = moveUpTest("""
            // - main.rs
            #[derive(Debug)]
            struct S;
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            #[derive(Debug)]
            struct S;
        """)

    fun `test step over struct with outer attr down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[derive(Debug)]
            struct S;
        """, """
            // - main.rs
            #[derive(Debug)]
            struct S;
            fn test() {

            }
        """)

    fun `test step over impl up`() = moveUpTest("""
            struct S;
            impl S {}
            fn /*caret*/test() {

            }
        """, """
            struct S;
            fn test() {

            }
            impl S {}
        """)

    fun `test step over impl down`() = moveDownTest("""
            struct S;
            fn /*caret*/test() {

            }
            impl S {}
        """, """
            struct S;
            impl S {}
            fn test() {

            }
        """)

    fun `test step over trait with attr up`() = moveUpTest("""
            // - main.rs
            #[test]
            trait S {}
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            #[test]
            trait S {}
        """)

    fun `test step over trait with attr down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[test]
            trait S {}
        """, """
            // - main.rs
            #[test]
            trait S {}
            fn test() {

            }
        """)

    fun `test step over trait up`() = moveUpTest("""
            // - main.rs
            trait S {

            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            trait S {

            }
        """)

    fun `test step over trait down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            trait S {

            }
        """, """
            // - main.rs
            trait S {

            }
            fn test() {

            }
        """)

    fun `test step over macro up`() = moveUpTest("""
            // - main.rs
            test!();
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            test!();
        """)

    fun `test step over macro down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            test!();
        """, """
            // - main.rs
            test!();
            fn test() {

            }
        """)

    fun `test step over macro multiline up`() = moveUpTest("""
            // - main.rs
            test!{

            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            test!{

            }
        """)

    fun `test step over macro multiline down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            test! {

            }
        """, """
            // - main.rs
            test! {

            }
            fn test() {

            }
        """)

    fun `test step over macro rules up`() = moveUpTest("""
            // - main.rs
            macro_rules! test {

            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            macro_rules! test {

            }
        """)

    fun `test step over macro rules down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            macro_rules! test {

            }
        """, """
            // - main.rs
            macro_rules! test {

            }
            fn test() {

            }
        """)

    fun `test step over macro rules one line up`() = moveUpTest("""
            // - main.rs
            macro_rules! test {}
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            macro_rules! test {}
        """)

    fun `test step over macro rules one line down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            macro_rules! test {}
        """, """
            // - main.rs
            macro_rules! test {}
            fn test() {

            }
        """)

    fun `test step over mod up`() = moveUpTest("""
            // - main.rs
            mod S {

            }
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            mod S {

            }
        """)

    fun `test step over mod down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            mod S {

            }
        """, """
            // - main.rs
            mod S {

            }
            fn test() {

            }
        """)

    fun `test step over extern crate up`() = moveUpTest("""
            // - main.rs
            extern crate test;
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            extern crate test;
        """)

    fun `test step over extern crate down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            extern crate test;
        """, """
            // - main.rs
            extern crate test;
            fn test() {

            }
        """)

    fun `test step over extern crate with attr up`() = moveUpTest("""
            // - main.rs
            #[macro_use]
            extern crate test;
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            #[macro_use]
            extern crate test;
        """)

    fun `test step over extern crate with attr down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #[macro_use]
            extern crate test;
        """, """
            // - main.rs
            #[macro_use]
            extern crate test;
            fn test() {

            }
        """)

    fun `test step over inner attr up`() = moveUpTest("""
            // - main.rs
            #![allow(bad_style)]
            fn /*caret*/test() {

            }
        """, """
            // - main.rs
            fn test() {

            }
            #![allow(bad_style)]
        """)

    fun `test step over inner attr down`() = moveDownTest("""
            // - main.rs
            fn /*caret*/test() {

            }
            #![allow(bad_style)]
        """, """
            // - main.rs
            #![allow(bad_style)]
            fn test() {

            }
        """)

    fun `test impl prevent step out up`() = moveUpTest("""
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
                fn test() {
                    test!();
                }
            }
        """)

    fun `test impl prevent step out down`() = moveDownTest("""
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
                fn test() {
                    test!();
                }
            }
        """)

    fun `test trait prevent step out up`() = moveUpTest("""
            // - main.rs
            trait S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            trait S {
                fn test() {
                    test!();
                }
            }
        """)

    fun `test trait prevent step out down`() = moveDownTest("""
            // - main.rs
            trait S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            trait S {
                fn test() {
                    test!();
                }
            }
        """)

    fun `test mod prevent step out up`() = moveUpTest("""
            // - main.rs
            mod s {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            mod s {
                fn test() {
                    test!();
                }
            }
        """)

    fun `test mod prevent step out down`() = moveDownTest("""
            // - main.rs
            mod s {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            mod s {
                fn test() {
                    test!();
                }
            }
        """)

    fun `test function prevent step out up`() = moveUpTest("""
            // - main.rs
            fn s() {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            fn s() {
                fn test() {
                    test!();
                }
            }
        """)

    fun `test function prevent step out down`() = moveDownTest("""
            // - main.rs
            fn s() {
                fn /*caret*/test() {
                    test!();
                }
            }
        """, """
            // - main.rs
            fn s() {
                fn test() {
                    test!();
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
