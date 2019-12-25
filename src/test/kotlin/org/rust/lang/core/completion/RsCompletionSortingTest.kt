/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldDecl
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

    fun `test inherited before non-inherited`() = doTest("""
        struct S;

        impl S {
            fn foo1() {}
            fn foo3() {}
        }

        trait T {
            fn foo2();
            fn foo4();
        }

        impl T for S {
            fn foo2() {}
            fn foo4() {}
        }

        fn foo() { S::/*caret*/ }
    """, listOf(
        RsFunction::class to "foo1",
        RsFunction::class to "foo3",
        RsFunction::class to "foo2",
        RsFunction::class to "foo4"
    ))

    fun `test assoc fns before methods`() = doTest("""
        struct S;

        impl S {
            fn foo1() {}
            fn foo3(&self) {}
            fn foo5() {}
            fn foo7(&self) {}
        }

        trait T {
            fn foo2();
            fn foo4(&self);
            fn foo6();
            fn foo8(&self);
        }

        impl T for S {
            fn foo2() {}
            fn foo4(&self) {}
            fn foo6() {}
            fn foo8(&self) {}
        }

        fn foo() { S::/*caret*/ }
    """, listOf(
        RsFunction::class to "foo1",
        RsFunction::class to "foo5",
        RsFunction::class to "foo2",
        RsFunction::class to "foo6",
        RsFunction::class to "foo3",
        RsFunction::class to "foo7",
        RsFunction::class to "foo4",
        RsFunction::class to "foo8"
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

            foo/*caret*/;
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

    fun `test expected types priority (let binding)`() = doTest("""
        struct foo1<T>(T);
        struct foo2<T>(T);

        const foo3: foo1<i32> = foo1(1);
        const foo4: foo1<f32> = foo1(1.0);
        const foo5: foo2<i32> = foo2(2);

        fn foo6(x: foo1<i32>) -> foo1<i32> {}
        fn foo7(x: foo1<f32>) -> foo1<f32> {}
        fn foo8(x: foo2<i32>) -> foo2<i32> {}

        macro_rules! foo9 {}

        enum E { foo10 }
        use E::foo10;

        fn bar(foo11: foo1<i32>, foo12: foo1<f32>, foo13: foo2<i32>) -> foo1<i32> {
            let foo14 = foo1(1);
            let foo15 = foo1(1.0);
            let foo16 = foo2(2);

            struct foo17;

            const foo18: foo1<i32> = foo1(1);
            const foo19: foo1<f32> = foo1(1.0);
            const foo20: foo2<i32> = foo2(2);

            fn foo21() -> foo1<i32> {}
            fn foo22() -> foo1<f32> {}
            fn foo23() -> foo2<i32> {}

            macro_rules! foo24 {}

            let x: foo1<i32> = foo/*caret*/;
        }
    """, listOf(
        RsPatBinding::class to "foo11",
        RsStructItem::class to "foo1",
        RsPatBinding::class to "foo14",
        RsConstant::class to "foo18",
        RsFunction::class to "foo21",
        RsStructItem::class to "foo2",
        RsConstant::class to "foo3",
        RsFunction::class to "foo6",
        RsPatBinding::class to "foo12",
        RsPatBinding::class to "foo13",
        RsPatBinding::class to "foo15",
        RsPatBinding::class to "foo16",
        RsStructItem::class to "foo17",
        RsConstant::class to "foo19",
        RsConstant::class to "foo20",
        RsFunction::class to "foo22",
        RsFunction::class to "foo23",
        RsMacro::class to "foo24",
        RsEnumVariant::class to "foo10",
        RsConstant::class to "foo4",
        RsConstant::class to "foo5",
        RsFunction::class to "foo7",
        RsFunction::class to "foo8",
        RsMacro::class to "foo9"
    ))

    fun `test expected types priority (fn arg)`() = doTest("""
        struct foo1<T>(T);
        struct foo2<T>(T);

        const foo3: foo1<i32> = foo1(1);
        const foo4: foo1<f32> = foo1(1.0);
        const foo5: foo2<i32> = foo2(2);

        fn foo6(x: foo1<i32>) -> foo1<i32> {}
        fn foo7(x: foo1<f32>) -> foo1<f32> {}
        fn foo8(x: foo2<i32>) -> foo2<i32> {}

        macro_rules! foo9 {}

        enum E { foo10 }
        use E::foo10;

        fn bar(foo11: foo1<i32>, foo12: foo1<f32>, foo13: foo2<i32>) -> foo1<i32> {
            let foo14 = foo1(1);
            let foo15 = foo1(1.0);
            let foo16 = foo2(2);

            struct foo17;

            const foo18: foo1<i32> = foo1(1);
            const foo19: foo1<f32> = foo1(1.0);
            const foo20: foo2<i32> = foo2(2);

            fn foo21() -> foo1<i32> {}
            fn foo22() -> foo1<f32> {}
            fn foo23() -> foo2<i32> {}

            macro_rules! foo24 {}

            foo6(foo/*caret*/);
        }
    """, listOf(
        RsPatBinding::class to "foo11",
        RsStructItem::class to "foo1",
        RsPatBinding::class to "foo14",
        RsConstant::class to "foo18",
        RsFunction::class to "foo21",
        RsStructItem::class to "foo2",
        RsConstant::class to "foo3",
        RsFunction::class to "foo6",
        RsPatBinding::class to "foo12",
        RsPatBinding::class to "foo13",
        RsPatBinding::class to "foo15",
        RsPatBinding::class to "foo16",
        RsStructItem::class to "foo17",
        RsConstant::class to "foo19",
        RsConstant::class to "foo20",
        RsFunction::class to "foo22",
        RsFunction::class to "foo23",
        RsMacro::class to "foo24",
        RsEnumVariant::class to "foo10",
        RsConstant::class to "foo4",
        RsConstant::class to "foo5",
        RsFunction::class to "foo7",
        RsFunction::class to "foo8",
        RsMacro::class to "foo9"
    ))

    fun `test expected types priority (return type)`() = doTest("""
        struct foo1<T>(T);
        struct foo2<T>(T);

        const foo3: foo1<i32> = foo1(1);
        const foo4: foo1<f32> = foo1(1.0);
        const foo5: foo2<i32> = foo2(2);

        fn foo6(x: foo1<i32>) -> foo1<i32> {}
        fn foo7(x: foo1<f32>) -> foo1<f32> {}
        fn foo8(x: foo2<i32>) -> foo2<i32> {}

        macro_rules! foo9 {}

        enum E { foo10 }
        use E::foo10;

        fn bar(foo11: foo1<i32>, foo12: foo1<f32>, foo13: foo2<i32>) -> foo1<i32> {
            let foo14 = foo1(1);
            let foo15 = foo1(1.0);
            let foo16 = foo2(2);

            struct foo17;

            const foo18: foo1<i32> = foo1(1);
            const foo19: foo1<f32> = foo1(1.0);
            const foo20: foo2<i32> = foo2(2);

            fn foo21() -> foo1<i32> {}
            fn foo22() -> foo1<f32> {}
            fn foo23() -> foo2<i32> {}

            macro_rules! foo24 {}

            foo/*caret*/
        }
    """, listOf(
        RsPatBinding::class to "foo11",
        RsStructItem::class to "foo1",
        RsPatBinding::class to "foo14",
        RsConstant::class to "foo18",
        RsFunction::class to "foo21",
        RsStructItem::class to "foo2",
        RsConstant::class to "foo3",
        RsFunction::class to "foo6",
        RsPatBinding::class to "foo12",
        RsPatBinding::class to "foo13",
        RsPatBinding::class to "foo15",
        RsPatBinding::class to "foo16",
        RsStructItem::class to "foo17",
        RsConstant::class to "foo19",
        RsConstant::class to "foo20",
        RsFunction::class to "foo22",
        RsFunction::class to "foo23",
        RsMacro::class to "foo24",
        RsEnumVariant::class to "foo10",
        RsConstant::class to "foo4",
        RsConstant::class to "foo5",
        RsFunction::class to "foo7",
        RsFunction::class to "foo8",
        RsMacro::class to "foo9"
    ))

    fun `test expected types priority (dot expr)`() = doTest("""
        struct foo1<T>(T);
        struct foo2<T>(T);

        struct S {
            foo3: foo1<i32>,
            foo4: foo1<f32>,
            foo5: foo2<i32>
        }

        impl S {
            fn foo6(self, x: foo1<i32>) -> foo1<i32> {}
            fn foo7(self, x: foo1<f32>) -> foo1<f32> {}
            fn foo8(self, x: foo2<i32>) -> foo2<i32> {}
        }

        fn bar() {
            let s = S {
                foo3: foo1(0),
                foo4: foo1(0.0),
                foo5: foo2(0)
            };
            let x: foo1<i32> = s./*caret*/;
        }
    """, listOf(
        RsFieldDecl::class to "foo3",
        RsFunction::class to "foo6",
        RsFieldDecl::class to "foo4",
        RsFieldDecl::class to "foo5",
        RsFunction::class to "foo7",
        RsFunction::class to "foo8"
    ))

    fun `test tuple field order`() = doTest("""
        fn main() {
            let tuple = (0, "", 0.0);
            let d: f64 = tuple./*caret*/
        }
    """, listOf(
        Int::class to "2",
        Int::class to "0",
        Int::class to "1"
    ))

    private fun doTest(@Language("Rust") code: String, expected: List<Pair<KClass<out Any>, String>>) {
        InlineFile(code).withCaret()
        val elements = myFixture.completeBasic()
        check(elements.size == expected.size) {
            "Wrong size of completion variants. Expected ${expected.size}, actual: ${elements.size}"
        }
        for ((actual, e) in elements.zip(expected)) {
            val lookupObject = actual.psiElement ?: actual.`object`
            val (klass, name) = e
            check(klass.isInstance(lookupObject)) {
                "Expected a ${klass.java.name}, found ${lookupObject.javaClass}"
            }
            val actualName = actual.lookupString
            check(name == actualName) { "Expected $name got $actualName" }
        }
    }
}
