/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.generate.RsStructMemberChooserObject
import org.rust.ide.refactoring.generate.StructMemberChooserUi
import org.rust.ide.refactoring.extractStructFields.ExtractFieldsUi
import org.rust.ide.refactoring.extractStructFields.withMockExtractFieldsUi
import org.rust.ide.refactoring.generate.withMockStructMemberChooserUi
import org.rust.launchAction

class RsExtractStructFieldsTest : RsTestBase() {
    fun `test unavailable on tuple struct`() = doUnavailableTest("""
        struct A(u32, u32/*caret*/);
    """)

    fun `test unavailable on unit struct`() = doUnavailableTest("""
        struct A/*caret*/;
    """)

    fun `test unavailable on empty block struct`() = doUnavailableTest("""
        struct A { /*caret*/ }
    """)

    fun `test unavailable on impl`() = doUnavailableTest("""
        struct A {
            a: u32
        }

        impl A {
            /*caret*/
        }
    """)

    fun `test simple`() = doAvailableTest("""
        struct A {
            a: u32/*caret*/
        }
    """, "S1", listOf("a"), """
        struct S1 {
            a: u32
        }

        struct A {
            s1: S1
        }
    """)

    fun `test keep existing fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: i32,
            c: bool
        }
    """, "S1", listOf("b"), """
        struct S1 {
            b: i32
        }

        struct A {
            a: u32,
            s1: S1,
            c: bool
        }
    """)

    fun `test field with same name`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
        }
    """, "B", listOf("a"), """
        struct B {
            a: u32
        }

        struct A {
            b0: B,
            b: u32,
        }
    """)

    fun `test multiple fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: i32,
            c: bool,
            d: u32
        }
    """, "S1", listOf("b", "c"), """
        struct S1 {
            b: i32,
            c: bool
        }

        struct A {
            a: u32,
            s1: S1,
            d: u32
        }
    """)

    fun `test keep supported attributes`() = doAvailableTest("""
        #[derive(Debug, Clone)]
        #[repr(C)]
        struct A {
            a: u32/*caret*/
        }
    """, "S1", listOf("a"), """
        #[derive(Debug, Clone)]
        #[repr(C)]
        struct S1 {
            a: u32
        }

        #[derive(Debug, Clone)]
        #[repr(C)]
        struct A {
            s1: S1
        }
    """)

    fun `test filter generic parameters`() = doAvailableTest("""
        struct A<T, R> {
            a: T,/*caret*/
            b: R
        }
    """, "S1", listOf("a"), """
        struct S1<T> {
            a: T
        }

        struct A<T, R> {
            s1: S1<T>,
            b: R
        }
    """)

    fun `test filter lifetimes`() = doAvailableTest("""
        struct A<'a, 'b> {
            a: &'a u32,/*caret*/
            b: &'b u32
        }
    """, "S1", listOf("a"), """
        struct S1<'a> {
            a: &'a u32
        }

        struct A<'a, 'b> {
            s1: S1<'a>,
            b: &'b u32
        }
    """)

    fun `test where clause`() = doAvailableTest("""
        trait Trait<S> {}
        struct A<T, R> where T: Trait<R> {
            a: T,/*caret*/
            b: R
        }
    """, "S1", listOf("a"), """
        trait Trait<S> {}

        struct S1<T, R> where T: Trait<R> {
            a: T
        }

        struct A<T, R> where T: Trait<R> {
            s1: S1<T, R>,
            b: R
        }
    """)

    fun `test visibility pub`() = doAvailableTest("""
        struct A {
            pub a: u32,/*caret*/
            pub(crate) b: u32
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            pub a: u32,
            pub(crate) b: u32
        }

        struct A {
            pub s1: S1
        }
    """)

    fun `test visibility pub(crate)`() = doAvailableTest("""
        struct A {
            pub(crate) a: u32,/*caret*/
            b: u32
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            pub(crate) a: u32,
            b: u32
        }

        struct A {
            pub(crate) s1: S1
        }
    """)

    fun `test visibility broadest module`() = doAvailableTest("""
        mod foo {
            mod bar {
                struct A {
                    pub(in crate::foo) a: u32,/*caret*/
                    pub(in crate::foo::bar) b: u32
                }
            }
        }
    """, "S1", listOf("a", "b"), """
        mod foo {
            mod bar {
                struct S1 {
                    pub(in crate::foo) a: u32,
                    pub(in crate::foo::bar) b: u32
                }

                struct A {
                    pub(in test_package::foo) s1: S1
                }
            }
        }
    """)

    fun `test replace literal usage`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32,
            d: bool
        }
        fn foo() -> A {
            A { a: 0, b: 1, c: 2, d: true }
        }
    """, "S1", listOf("b", "c"), """
        struct S1 {
            b: u32,
            c: u32
        }

        struct A {
            a: u32,
            s1: S1,
            d: bool
        }
        fn foo() -> A {
            A { a: 0, s1: S1 { b: 1, c: 2 }, d: true }
        }
    """)

    fun `test replace literal usage last literal field`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn foo() -> A {
            A { a: 0, b: 1 }
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1
        }
        fn foo() -> A {
            A { s1: S1 { a: 0, b: 1 } }
        }
    """)

    fun `test replace literal usage with shorthand`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32,
            d: bool
        }
        fn foo() -> A {
            let b = 5;
            A { a: 0, b, c: 2, d: true }
        }
    """, "S1", listOf("b", "c"), """
        struct S1 {
            b: u32,
            c: u32
        }

        struct A {
            a: u32,
            s1: S1,
            d: bool
        }
        fn foo() -> A {
            let b = 5;
            A { a: 0, s1: S1 { b, c: 2 }, d: true }
        }
    """)

    fun `test replace literal usage import new struct`() = doAvailableTest("""
        mod foo {
            pub struct A {
                pub a: u32,/*caret*/
            }
        }
        fn bar() -> foo::A {
            foo::A { a: 0 }
        }
    """, "S1", listOf("a"), """
        use foo::S1;

        mod foo {
            pub struct S1 {
                pub a: u32
            }

            pub struct A {
                pub s1: S1,
            }
        }
        fn bar() -> foo::A {
            foo::A { s1: S1 { a: 0 } }
        }
    """)

    fun `test replace literal usage dotdot all fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            A { ..foo() }
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            let a = foo();
            A { s1: S1 { a: a.s1.a, b: a.s1.b }, ..a }
        }
    """)

    fun `test replace literal usage dotdot let decl`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            let x = A { ..foo() };
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            let a = foo();
            let x = A { s1: S1 { a: a.s1.a, b: a.s1.b }, ..a };
        }
    """)

    fun `test replace literal usage dotdot subset of fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            A { ..foo() }
        }
    """, "S1", listOf("a"), """
        struct S1 {
            a: u32
        }

        struct A {
            s1: S1,
            b: u32
        }
        fn foo() -> A { unimplemented!() }
        fn bar() -> A {
            let a = foo();
            A { s1: S1 { a: a.s1.a }, ..a }
        }
    """)

    fun `test replace literal usage dotdot path expr`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn bar(a: A) -> A {
            A { ..a }
        }
    """, "S1", listOf("a"), """
        struct S1 {
            a: u32
        }

        struct A {
            s1: S1,
            b: u32
        }
        fn bar(a: A) -> A {
            A { s1: S1 { a: a.s1.a }, ..a }
        }
    """)

    fun `test replace pat literal usage all fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32
        }
        fn foo(x: A) {
            let A { a, b } = x;
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1
        }
        fn foo(x: A) {
            let A { s1: S1 { a, b } } = x;
        }
    """)

    fun `test replace pat literal usage subset of fields`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32
        }
        fn foo(x: A) {
            let A { a, b, c } = x;
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1,
            c: u32
        }
        fn foo(x: A) {
            let A { s1: S1 { a, b }, c } = x;
        }
    """)

    fun `test replace pat literal usage full rest pat`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32
        }
        fn foo(x: A) {
            let A { .. } = x;
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1,
            c: u32
        }
        fn foo(x: A) {
            let A { s1: S1 { .. }, .. } = x;
        }
    """)

    fun `test replace pat literal usage partial rest pat`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32
        }
        fn foo(x: A) {
            let A { a: foo, .. } = x;
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1,
            c: u32
        }
        fn foo(x: A) {
            let A { s1: S1 { a: foo, .. }, .. } = x;
        }
    """)

    fun `test replace field usage`() = doAvailableTest("""
        struct A {
            a: u32,/*caret*/
            b: u32,
            c: u32
        }
        fn foo(a: A) {
            let x = a.a;
            let y = a.b;
        }
    """, "S1", listOf("a", "b"), """
        struct S1 {
            a: u32,
            b: u32
        }

        struct A {
            s1: S1,
            c: u32
        }
        fn foo(a: A) {
            let x = a.s1.a;
            let y = a.s1.b;
        }
    """)

    fun `test replace nested field usage`() = doAvailableTest("""
        struct B {
            x: u32
        }
        struct A {
            a: u32,/*caret*/
            b: B,
            c: u32
        }
        fn foo(a: A) {
            let x = a.b.x;
        }
    """, "Foo", listOf("a", "b"), """
        struct B {
            x: u32
        }

        struct Foo {
            a: u32,
            b: B
        }

        struct A {
            foo: Foo,
            c: u32
        }
        fn foo(a: A) {
            let x = a.foo.b.x;
        }
    """)

    private fun doAvailableTest(
        @Language("Rust") before: String,
        structName: String,
        selected: List<String>,
        @Language("Rust") after: String
    ) {
        withMockStructMemberChooserUi(object : StructMemberChooserUi {
            override fun selectMembers(
                project: Project,
                all: List<RsStructMemberChooserObject>
            ): List<RsStructMemberChooserObject> {
                return all.filter { it.member.argumentIdentifier in selected }
            }
        }) {
            withMockExtractFieldsUi(object : ExtractFieldsUi {
                override fun selectStructName(project: Project): String = structName
            }) {
                checkEditorAction(before, after, "Rust.RsExtractStructFields")
            }
        }
    }

    private fun doUnavailableTest(@Language("Rust") code: String) {
        InlineFile(code.trimIndent()).withCaret()
        myFixture.launchAction("Rust.RsExtractStructFields", shouldBeEnabled = false)
    }
}
