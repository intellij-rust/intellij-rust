/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsMacroCallBodyCompletionTest : RsCompletionTestBase() {
    fun `test simple`() = doSingleCompletion("""
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        struct FooBar;
        foo! {
            fn bar() {
                F/*caret*/
            }
        }
    """, """
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        struct FooBar;
        foo! {
            fn bar() {
                FooBar/*caret*/
            }
        }
    """)

    fun `test complete struct from the same macro`() = doSingleCompletion("""
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            struct FooBar;
            fn bar() {
                F/*caret*/
            }
        }
    """, """
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            struct FooBar;
            fn bar() {
                FooBar/*caret*/
            }
        }
    """)

    fun `test nested macro`() = doSingleCompletion("""
        macro_rules! foo {
            ($ i:item $($ rest:tt)*) => { $ i foo!($($ rest)*); };
            () => {}
        }
        foo! {
            struct FooBar;
            fn bar() {
                F/*caret*/
            }
        }
    """, """
        macro_rules! foo {
            ($ i:item $($ rest:tt)*) => { $ i foo!($($ rest)*); };
            () => {}
        }
        foo! {
            struct FooBar;
            fn bar() {
                FooBar/*caret*/
            }
        }
    """)

    // TODO extra `()`
    fun `test method`() = doSingleCompletion("""
        macro_rules! foo {
            ($ e:expr, $ i:ident) => { fn foo() { $ e.$ i(); } };
        }
        struct Foo;
        impl Foo { fn bar(&self) {} }
        foo!(Foo, b/*caret*/);
    """, """
        macro_rules! foo {
            ($ e:expr, $ i:ident) => { fn foo() { $ e.$ i(); } };
        }
        struct Foo;
        impl Foo { fn bar(&self) {} }
        foo!(Foo, bar()/*caret*/);
    """)
}
