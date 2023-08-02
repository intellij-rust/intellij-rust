/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockRustcVersion
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class EncloseExprInBracesFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test const generic default`() = checkFixByText("Enclose the expression in braces", """
        struct S<const N: usize = <error>/*caret*/1 + 1</error>>;
    """, """
        struct S<const N: usize = { 1 + 1 }>;
    """)

    @MockRustcVersion("1.70.0")
    fun `test const generic default unavailable 1`() = checkFixIsUnavailable("Enclose the expression in braces", """
        struct S<const N: usize = /*caret*/1>;
    """)

    @MockRustcVersion("1.70.0")
    fun `test const generic default unavailable 2`() = checkFixIsUnavailable("Enclose the expression in braces", """
        struct S<const N: usize = /*caret*/{ 1 + 1 }>;
    """)

    fun `test const generic argument`() = checkFixByText("Enclose the expression in braces", """
        struct S<const N: usize>;
        fn main() {
            S::<<error>/*caret*/1 + 1</error>>;
        }
    """, """
        struct S<const N: usize>;
        fn main() {
            S::<{ 1 + 1 }>;
        }
    """)

    fun `test const generic argument unavailable 1`() = checkFixIsUnavailable("Enclose the expression in braces", """
        struct S<const N: usize>;
        fn main() {
            S::</*caret*/1>;
        }
    """)

    fun `test const generic argument unavailable 2`() = checkFixIsUnavailable("Enclose the expression in braces", """
        struct S<const N: usize>;
        fn main() {
            S::</*caret*/{ 1 + 1 }>;
        }
    """)

    @MockRustcVersion("1.60.0-nightly")
    fun `test const generic all expressions`() = checkErrors("""
        #![feature(min_const_generics)]
        #![feature(adt_const_params)]
        #![feature(const_generics_defaults)]

        use std::ops::Neg;

        #[derive(PartialEq, Eq)]
        struct S1;
        impl Neg for S1 {
            type Output = Self;
            fn neg(self) -> Self::Output { self }
        }

        #[derive(PartialEq, Eq)]
        struct S2 { x: usize }

        #[derive(PartialEq, Eq)]
        struct S3(usize);
        const C: S3 = S3(1);

        mod a { pub struct S4; }

        const fn foo(x: usize) -> usize { x }

        struct F0<const N: S1 = S1>();
        struct F1<const N: m::S4 = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">m::S4</error>>();
        struct F2<const N: usize = 1>();
        struct F3<const N: usize = { 1 + 1 }>();
        struct F4<const N: usize = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">1 + 1</error>>();
        struct F5<const N: [usize; 1] = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">[1]</error>>();
        struct F6<const N: usize = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">1 as usize</error>>();
        struct F7<const N: usize = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">foo(42)</error>>();
        struct F8<const N: S2 = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">S2 { x: 1 }</error>>();
        struct F9<const N: usize = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">C.0</error>>();
        struct F10<const N: isize = -1>();
        struct F11<const N: S1 = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">-S1</error>>();
        struct F12<const N: bool = <error descr="Expressions must be enclosed in braces to be used as const generic arguments">!true</error>>();

        fn main() {
            // F0::<S1>();
            // F1::<m::S4>();
            F2::<1>();
            F3::<{ 1 + 1 }>();
            F4::<<error descr="Expressions must be enclosed in braces to be used as const generic arguments">1 + 1</error>>();
            // F5::<[1]>();
            F6::<<error descr="Expressions must be enclosed in braces to be used as const generic arguments">1 as usize</error>>();
            // F7::<foo(42)();
            // F8::<S2 { x: 1 }>();
            // F9::<C.0>();
             F10::<-1>();
             F11::<<error descr="Expressions must be enclosed in braces to be used as const generic arguments">-S1</error>>();
            // F12::<!true>();
        }
    """)
}
