/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.psi.NavigatablePsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.*
import kotlin.reflect.KClass


class RsCompletionSortingTest : RsTestBase() {
    fun `test macros are low priority`() = doTest("""
        fn foo_bar() {}
        macro_rules! foo_bar {}

        fn _foo_bar() {}
        macro_rules! _foo_bar {}

        fn main() {
            foo/*caret*/
        }
    """, listOf(
        RsFunction::class to "foo_bar",
        RsFunction::class to "_foo_bar",
        RsMacro::class to "foo_bar",
        RsMacro::class to "_foo_bar"
    ))

    fun `test named fields before members`() = doTest("""
        struct S  { foo: i32 }
        impl S { fn foo(&self) {} }

        fn bar(a: S) { a./*caret*/ }
    """, listOf(
        RsNamedFieldDecl::class to "foo",
        RsFunction::class to "foo"
    ))

    fun `test tuple fields before members`() = doTest("""
        struct S(i32);
        impl S { fn foo(&self) {} }

        fn bar(a: S) { a./*caret*/ }
    """, listOf(
        RsTupleFieldDecl::class to "0",
        RsFunction::class to "foo"
    ))

    fun `test enum variants before associated constants`() = doTest("""
        enum E { A, B }
        trait T { const A: i32; }
        impl T for E { const A: i32 = 0; }

        fn main() { E::/*caret*/ }
    """, listOf(
        RsEnumVariant::class to "A",
        RsEnumVariant::class to "B",
        RsConstant::class to "A"
    ))

    fun `test inherent impl methods before trait impl methods`() = doTest("""
        struct S;
        trait T { fn a(&self); }
        impl T for S { fn a(&self) {} }
        impl S { fn b(&self) {} }

        fn foo(a: S) { a./*caret*/ }
    """, listOf(
        RsFunction::class to "b",
        RsFunction::class to "a"
    ))

    fun `test locals before non-locals`() = doTest("""
        struct foo2;
        const foo3: () = ();
        fn foo4() {}
        macro_rules! foo5 {}

        enum E { foo1 }
        use E::foo1;

        fn bar(foo5: ()) {
            let foo6 = 0;
            struct foo7;
            const foo8: () = ();
            fn foo9() {}
            macro_rules! foo10 {}

            foo/*caret*/
        }
    """, listOf(
        RsPatBinding::class to "foo5",
        RsPatBinding::class to "foo6",
        RsStructItem::class to "foo7",
        RsConstant::class to "foo8",
        RsFunction::class to "foo9",
        RsMacro::class to "foo10",
        RsEnumVariant::class to "foo1",
        RsStructItem::class to "foo2",
        RsConstant::class to "foo3",
        RsFunction::class to "foo4",
        RsMacro::class to "foo5"
    ))

    private fun doTest(@Language("Rust") code: String, expected: List<Pair<KClass<out NavigatablePsiElement>, String>>) {
        InlineFile(code).withCaret()
        val elements = myFixture.completeBasic()
            .map { it.psiElement!! as NavigatablePsiElement }
        check(elements.size == expected.size) {
            "Wrong size of completion variants. Expected ${expected.size}, actual: ${elements.size}"
        }
        for ((actual, e) in elements.zip(expected)) {
            val (klass, name) = e
            check(klass.isInstance(actual)) {
                "Expected a ${klass.java.name}, found ${actual.javaClass}"
            }
            val actualName = actual.name
            check(name == actualName) { "Expected $name got $actualName" }
        }
    }
}
