/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_TYPE_DECLARATION
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsGotoTypeDeclarationTest : RsTestBase() {

    fun `test function declaration`() = doTest("""
        struct Foo;
        fn foo/*caret*/() -> Foo { unimplemented!() }
    """, """
        struct /*caret*/Foo;
        fn foo() -> Foo { unimplemented!() }
    """)

    fun `test method declaration`() = doTest("""
        enum Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar/*caret*/(&self) -> Bar { unimplemented!() }
        }
    """, """
        enum /*caret*/Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar(&self) -> Bar { unimplemented!() }
        }
    """)

    fun `test field declaration`() = doTest("""
        struct Bar;
        struct Foo {
            bar/*caret*/: Bar
        }
    """, """
        struct /*caret*/Bar;
        struct Foo {
            bar: Bar
        }
    """)

    fun `test const declaration`() = doTest("""
        struct Foo;
        const FOO/*caret*/: Foo = Foo;
    """, """
        struct /*caret*/Foo;
        const FOO: Foo = Foo;
    """)

    fun `test variable declaration`() = doTest("""
        struct Foo;
        fn main() {
            let a/*caret*/ = Foo;
        }
    """, """
        struct /*caret*/Foo;
        fn main() {
            let a = Foo;
        }
    """)

    fun `test argument declaration`() = doTest("""
        struct Foo;
        fn foo(foo/*caret*/: Foo) { unimplemeneted!() }
    """, """
        struct /*caret*/Foo;
        fn foo(foo: Foo) { unimplemeneted!() }
    """)

    fun `test function call`() = doTest("""
        struct Foo;
        fn foo() -> Foo { unimplemented!() }
        fn main() {
            foo/*caret*/();
        }
    """, """
        struct /*caret*/Foo;
        fn foo() -> Foo { unimplemented!() }
        fn main() {
            foo();
        }
    """)

    fun `test method call`() = doTest("""
        enum Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar(&self) -> Bar { unimplemented!() }
        }
        fn main() {
            Foo.bar/*caret*/();
        }
    """, """
        enum /*caret*/Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar(&self) -> Bar { unimplemented!() }
        }
        fn main() {
            Foo.bar();
        }
    """)

    fun `test field using`() = doTest("""
        struct Bar;
        struct Foo {
            bar: Bar
        }
        fn foo(foo: Foo) -> Bar {
            foo.bar/*caret*/
        }
    """, """
        struct /*caret*/Bar;
        struct Foo {
            bar: Bar
        }
        fn foo(foo: Foo) -> Bar {
            foo.bar
        }
    """)

    fun `test variable using`() = doTest("""
        struct Foo;
        fn main() {
            let a = Foo;
            a/*caret*/;
        }
    """, """
        struct /*caret*/Foo;
        fn main() {
            let a = Foo;
            a;
        }
    """)

    fun `test reference type`() = doTest("""
        struct Foo;
        fn foo/*caret*/<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """, """
        struct /*caret*/Foo;
        fn foo<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """)

    fun `test pointer type`() = doTest("""
        struct Foo;
        fn foo/*caret*/() -> *const Foo { unimplemented!() }
    """, """
        struct /*caret*/Foo;
        fn foo() -> *const Foo { unimplemented!() }
    """)

    fun `test array type`() = doTest("""
        struct Foo;
        fn foo/*caret*/() -> [Foo; 2] { unimplemented!() }
    """, """
        struct /*caret*/Foo;
        fn foo() -> [Foo; 2] { unimplemented!() }
    """)

    fun `test slice type`() = doTest("""
        struct Foo;
        fn foo/*caret*/<'a>(_: &'a i32) -> &'a [Foo] { unimplemented!() }
    """, """
        struct /*caret*/Foo;
        fn foo<'a>(_: &'a i32) -> &'a [Foo] { unimplemented!() }
    """)

    fun `test trait object type`() = doTest("""
        trait Foo {}
        fn foo/*caret*/<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """, """
        trait /*caret*/Foo {}
        fn foo<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """)

    fun `test type parameter`() = doTest("""
        fn foo<T>() -> T { unimplemented!() }
        fn main() {
            let x = foo/*caret*/::<i32>();
        }
    """, """
        fn foo</*caret*/T>() -> T { unimplemented!() }
        fn main() {
            let x = foo::<i32>();
        }
    """)

    fun `test associated type`() = doTest("""
        trait Foo {
            type Bar;
            fn foo(&self) -> Self::Bar;
        }
        struct Baz;
        struct Qwe;
        impl Foo for Baz {
            type Bar = Qwe;

            fn foo(&self) -> Self::Bar {
                unimplemented!()
            }
        }

        fn main() {
            let x = Baz.foo/*caret*/();
        }
    """, """
        trait Foo {
            type /*caret*/Bar;
            fn foo(&self) -> Self::Bar;
        }
        struct Baz;
        struct Qwe;
        impl Foo for Baz {
            type Bar = Qwe;

            fn foo(&self) -> Self::Bar {
                unimplemented!()
            }
        }

        fn main() {
            let x = Baz.foo();
        }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) = checkByText(before, after) {
        myFixture.performEditorAction(ACTION_GOTO_TYPE_DECLARATION)
    }
}
