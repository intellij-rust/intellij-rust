/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_TYPE_DECLARATION
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsGotoTypeDeclarationTest : RsTestBase() {

    fun `test function declaration`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo/*caret_before*/() -> Foo { unimplemented!() }
    """)

    fun `test method declaration`() = doTest("""
        enum /*caret_after*/Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar/*caret_before*/(&self) -> Bar { unimplemented!() }
        }
    """)

    fun `test field declaration`() = doTest("""
        struct /*caret_after*/Bar;
        struct Foo {
            bar/*caret_before*/: Bar
        }
    """)

    fun `test const declaration`() = doTest("""
        struct /*caret_after*/Foo;
        const FOO/*caret_before*/: Foo = Foo;
    """)

    fun `test variable declaration`() = doTest("""
        struct /*caret_after*/Foo;
        fn main() {
            let a/*caret_before*/ = Foo;
        }
    """)

    fun `test argument declaration`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo(foo/*caret_before*/: Foo) { unimplemeneted!() }
    """)

    fun `test function call`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo() -> Foo { unimplemented!() }
        fn main() {
            foo/*caret_before*/();
        }
    """)

    fun `test method call`() = doTest("""
        enum /*caret_after*/Bar { BAR }
        struct Foo;
        impl Foo {
            fn bar(&self) -> Bar { unimplemented!() }
        }
        fn main() {
            Foo.bar/*caret_before*/();
        }
    """)

    fun `test field using`() = doTest("""
        struct /*caret_after*/Bar;
        struct Foo {
            bar: Bar
        }
        fn foo(foo: Foo) -> Bar {
            foo.bar/*caret_before*/
        }
    """)

    fun `test variable using`() = doTest("""
        struct /*caret_after*/Foo;
        fn main() {
            let a = Foo;
            a/*caret_before*/;
        }
    """)

    fun `test reference type`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo/*caret_before*/<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """)

    fun `test pointer type`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo/*caret_before*/() -> *const Foo { unimplemented!() }
    """)

    fun `test array type`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo/*caret_before*/() -> [Foo; 2] { unimplemented!() }
    """)

    fun `test slice type`() = doTest("""
        struct /*caret_after*/Foo;
        fn foo/*caret_before*/<'a>(_: &'a i32) -> &'a [Foo] { unimplemented!() }
    """)

    fun `test trait object type`() = doTest("""
        trait /*caret_after*/Foo {}
        fn foo/*caret_before*/<'a>(_: &'a i32) -> &'a Foo { unimplemented!() }
    """)

    fun `test type parameter`() = doTest("""
        fn foo</*caret_after*/T>() -> T { unimplemented!() }
        fn main() {
            let x = foo/*caret_before*/::<i32>();
        }
    """)

    fun `test associated type`() = doTest("""
        trait Foo {
            type Bar;
            fn foo(&self) -> Self::Bar;
        }
        struct Baz;
        struct /*caret_after*/Qwe;
        impl Foo for Baz {
            type Bar = Qwe;

            fn foo(&self) -> Self::Bar {
                unimplemented!()
            }
        }

        fn main() {
            let x = Baz.foo/*caret_before*/();
        }
    """)

    fun `test impl trait`() = doTest("""
        trait /*caret_after*/Foo {}
        fn foo() -> impl Foo { unimplemented!() }

        fn main() {
            foo/*caret_before*/();
        }
    """)

    fun `test self param in impl`() = doTest("""
        struct /*caret_after*/Foo;
        impl Foo {
            fn bar(/*caret_before*/self) {}
        }
    """)

    fun `test &self param in impl`() = doTest("""
        struct /*caret_after*/Foo;
        impl Foo {
            fn bar(&self/*caret_before*/) {}
        }
    """)

    fun `test &mut self param in impl`() = doTest("""
        struct /*caret_after*/Foo;
        impl Foo {
            fn bar(&mut /*caret_before*/self) {}
        }
    """)

    fun `test &mut self param in impl with spacing`() = doTest("""
        struct /*caret_after*/Foo;
        impl Foo {
            fn bar(  &  mut /*caret_before*/self) {}
        }
    """)

    fun `test self param in trait`() = doTest("""
        trait /*caret_after*/Foo {
            fn bar(/*caret_before*/self) {}
        }
    """)

    fun `test &self param in trait`() = doTest("""
        trait /*caret_after*/Foo {
            fn bar(&self/*caret_before*/) {}
        }
    """)

    fun `test &mut self param in trait`() = doTest("""
        trait /*caret_after*/Foo {
            fn bar(&mut /*caret_before*/self) {}
        }
    """)

    fun `test &mut self param in trait with spacing`() = doTest("""
        trait /*caret_after*/Foo {
            fn bar(  &  mut /*caret_before*/self) {}
        }
    """)

    fun `test type declared by a macro`() = doTest("""
        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* }; }
        as_is! { struct /*caret_after*/S; }
        fn main() {
            let /*caret_before*/a = S;
        }
    """)

    private fun doTest(@Language("Rust") code: String) = checkCaretMove(code) {
        myFixture.performEditorAction(ACTION_GOTO_TYPE_DECLARATION)
    }
}
