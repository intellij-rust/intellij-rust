/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

/**
 * Note that other [RsSelectionHandlerTestBase] tests are also tests for [RsMacroCallSelectionHandler]
 * because they use [RsSelectionHandlerTestBase.doTest] that repeats a test inside a macro call.
 */
class RsMacroCallSelectionHandlerTest : RsSelectionHandlerTestBase() {
    fun `test nested expansions 1`() = doTestWithoutMacro("""
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                foo!(<caret>2+2+2)+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                foo!(<selection><caret>2</selection>+2+2)+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                foo!(<selection><caret>2+2</selection>+2)+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                foo!(<selection><caret>2+2+2</selection>)+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                foo!<selection>(<caret>2+2+2)</selection>+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                <selection>foo!(<caret>2+2+2)</selection>+2;
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        foo! {
            fn bar() {
                <selection>foo!(<caret>2+2+2)+2</selection>;
            }
        }
    """)

    fun `test nested expansions 2`() = doTestWithoutMacro("""
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <caret>2+2+2+2+2
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <selection><caret>2</selection>+2+2+2+2
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <selection><caret>2+2</selection>+2+2+2
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <selection><caret>2+2+2</selection>+2+2
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <selection><caret>2+2+2+2</selection>+2
            }
        }
    """, """
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* } }
        macro_rules! bar {
            (fn bar() { $ t1:tt + $ t2:tt + $ t3:tt + $ t4:tt + $ t5:tt }) => { fn bar() { foo!($ t1 + $ t2 + $ t3) + $ t4 + $ t5; } }
        }
        bar! {
            fn bar() {
                <selection><caret>2+2+2+2+2</selection>
            }
        }
    """)

    fun `test selection separated with subtree`() = doTestWithoutMacro("""
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<caret>2 + 2 + 2 ] + 2 + 2);
        }
    """, """
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<selection><caret>2</selection> + 2 + 2 ] + 2 + 2);
        }
    """, """
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<selection><caret>2 + 2</selection> + 2 ] + 2 + 2);
        }
    """, """
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<selection><caret>2 + 2 + 2</selection> ] + 2 + 2);
        }
    """, """
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<selection><caret>2 + 2 + 2 ] + 2</selection> + 2);
        }
    """, """
        macro_rules! foo { ( [$($ t1:tt)*] + $($ t2:tt)* ) => { $($ t1)* + $($ t2)* } }
        fn foo() {
            foo!( [<selection><caret>2 + 2 + 2 ] + 2 + 2</selection>);
        }
    """)
}
