/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

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

    fun `test repr on empty enum`() = checkFixByText("Remove attribute `repr`", """
        #[<error descr="Enum with no variants can't have `repr` attribute [E0084]">repr/*caret*/</error>(u8)]
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

    fun `test derive on function`() = checkFixByText("Remove attribute `derive`","""
        <error descr="`derive` may only be applied to structs, enums and unions">#[derive(Debug)]</error>
        fn foo() { }
    """, """
        fn foo() { }
    """)
}
