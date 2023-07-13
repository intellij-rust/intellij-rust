/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.annotator.RsSyntaxErrorsAnnotator

class RemoveElementFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test inline on struct`() = checkFixByText("Remove attribute `inline`", """
        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
        struct Test {}
    """, """
        struct Test {}
    """)

    fun `test inline(always) on enum`() = checkFixByText("Remove attribute `inline`", """
        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>(always)]
        enum Test {}
    """, """
        enum Test {}
    """)

    fun `test remove colon colon in type ref`() = checkFixByText("Remove `::`", """
        type A = Vec<weak_warning descr="Redundant `::`">::/*caret*/</weak_warning><i32>;
    """, """
        type A = Vec<i32>;
    """, checkWeakWarn = true)

    fun `test remove colon colon in trait ref`() = checkFixByText("Remove `::`", """
        trait Foo<T> {}
        impl<T> Foo<weak_warning descr="Redundant `::`">::/*caret*/</weak_warning><T> for T {}
    """, """
        trait Foo<T> {}
        impl<T> Foo<T> for T {}
    """, checkWeakWarn = true)

    fun `test remove colon colon in Fn types`() = checkFixByText("Remove `::`", """
        type IntFn = Fn<weak_warning descr="Redundant `::`">::/*caret*/</weak_warning>(i32) -> i32;
    """, """
        type IntFn = Fn(i32) -> i32;
    """, checkWeakWarn = true)

    fun `test remove colon colon in expr`() = checkFixIsUnavailable("Remove `::`", """
        fn main() {
            let v = Vec::/*caret*/<i32>::new();
        }
    """, checkWeakWarn = true)

    fun `test remove colon colon in array size expr`() = checkFixIsUnavailable("Remove `::`", """
        use std::mem::size_of;

        fn main() {
            let b: &[u8; size_of::/*caret*/<i32>()];
        }
    """, checkWeakWarn = true)

    fun `test derive on function`() = checkFixByText("Remove attribute `derive`","""
        <error descr="`derive` may only be applied to structs, enums and unions [E0774]">/*caret*/#[derive(Debug)]</error>
        fn foo() { }
    """, """
        fn foo() { }
    """)

    fun `test remove visibility qualifier impl`() = checkFixByText("Remove visibility qualifier", """
        struct S;
        /*error descr="Unnecessary visibility qualifier [E0449]"*/pub/*caret*//*error**/ impl S {
            fn foo() {}
        }
    """, """
        struct S;
        impl S {
            fn foo() {}
        }
    """)

    fun `test remove visibility qualifier enum variant`() = checkFixByText("Remove visibility qualifier", """
        enum E {
            <error descr="Unnecessary visibility qualifier [E0449]">pub/*caret*/(crate)</error>V
        }
    """, """
        enum E {
            V
        }
    """)
}

class RemoveElementFixSyntaxTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test default parameter values (fn)`() = checkFixByText("Remove default parameter value", """
        fn foo(x: i32 = <error descr="Default parameter values are not supported in Rust">0/*caret*/</error>) {}
    """, """
        fn foo(x: i32) {}
    """)

    fun `test default parameter values (struct)`() = checkFixByText("Remove default parameter value", """
        struct S { x: i32 = <error descr="Default parameter values are not supported in Rust">0/*caret*/</error> }
    """, """
        struct S { x: i32 }
    """)

    fun `test default parameter values (tuple)`() = checkFixByText("Remove default parameter value", """
        struct T(i32 = <error descr="Default parameter values are not supported in Rust">0/*caret*/</error>);
    """, """
        struct T(i32);
    """)

    fun `test remove impl in type bound`() = checkFixByText("Remove `impl` keyword", """
        fn foo<U: <error descr="Expected trait bound, found `impl Trait` type">impl/*caret*/</error> T>() {}
    """, """
        fn foo<U: T>() {}
    """)

    fun `test remove dyn in type bound`() = checkFixByText("Remove `dyn` keyword", """
        fn foo<U: <error descr="Invalid `dyn` keyword">dyn/*caret*/</error> T>() {}
    """, """
        fn foo<U: T>() {}
    """)

    fun `test struct inheritance`() = checkFixByText("Remove super structs", """
        struct A; struct B;
        struct C : <error descr="Struct inheritance is not supported in Rust">A,/*caret*/ B</error>;
    """, """
        struct A; struct B;
        struct C;
    """)
}
