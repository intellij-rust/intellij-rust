/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsConstReferStaticInspectionTest : RsInspectionsTestBase(RsConstReferStaticInspection::class) {

    fun `test const refer to static`() = checkErrors("""
        static X: usize = 0;
        const Y: usize = <error descr="Const `Y` cannot refer to static `X` [E0013]">X</error>;
    """)

    fun `test const refer to static within expression`() = checkErrors("""
        static X: usize = 0;
        const Y: usize = <error descr="Const `Y` cannot refer to static `X` [E0013]">X</error> + 5;
    """)

    fun `test const refer to static within complex expression`() = checkErrors("""
        static X: usize = 0;
        const Y: bool = 0 + (<error descr="Const `Y` cannot refer to static `X` [E0013]">X</error> - 42) / 2 == 0;
    """)

    fun `test const refer to static with extra modifier`() = checkErrors("""
        pub static X: usize = 0;
        const Y: usize = <error descr="Const `Y` cannot refer to static `X` [E0013]">X</error>;
    """)

    fun `test const refer to static within const function call`() = checkErrors("""
        static X: usize = 0;

        const fn times_five(x: usize) -> usize {
            x * 5
        }

        const Y: usize = times_five(<error descr="Const `Y` cannot refer to static `X` [E0013]">X</error>) / 5;
    """)

    fun `test const refer to static within tuple`() = checkErrors("""
        static X: usize = 0;
        const Y: (usize, usize) = (<error descr="Const `Y` cannot refer to static `X` [E0013]">X</error>, 42);
    """)

    fun `test const refer to static within block expr`() = checkErrors("""
        static X: usize = 0;
        const Y: usize = { <error descr="Const `Y` cannot refer to static `X` [E0013]">X</error> };
    """)

    fun `test const refer to static within array literal`() = checkErrors("""
        static X: usize = 1;
        const Y: [usize; 3] = [0, <error descr="Const `Y` cannot refer to static `X` [E0013]">X</error>, 2];
    """)

    fun `test static reference in const array size`() = checkErrors("""
        static SIZE: usize = 5;

        fn main() {
            let _: [usize; <error descr="Array size cannot refer to static `SIZE` [E0013]">SIZE</error>];
        }
    """)

    fun `test static reference in expression of const array size`() = checkErrors("""
        static SIZE: usize = 5;

        fn main() {
            let _: [usize; 4 + <error descr="Array size cannot refer to static `SIZE` [E0013]">SIZE</error>];
        }
    """)

    fun `test static reference in enum discriminant`() = checkErrors("""
        static X: isize = 5;

        enum Bad {
            First = <error descr="Enum variant `First`'s discriminant value cannot refer to static `X` [E0013]">X</error>,
            Second
        }
    """)

    fun `test const reference in enum discriminant`() = checkErrors("""
        const X: isize = 5;

        enum Good {
            First = X,
            Second
        }
    """)

    // Even though E0015 will be emitted here, E0013 is emitted as well.
    fun `test const call to non-const fn with static argument`() = checkErrors("""
        fn times_five(x: usize) -> usize {
            x * 5
        }

        static X: usize = 2;
        const TEN: usize = times_five(<error descr="Const `TEN` cannot refer to static `X` [E0013]">X</error>);
    """)

    // Valid code: const may refer to const.
    fun `test const refer to const`() = checkErrors("""
        const X: usize = 0;
        const Y: usize = X;
    """)

    // Valid code: static may refer to static.
    fun `test static refer to static`() = checkErrors("""
        static X: usize = 0;
        static Y: usize = X;
    """)

    fun `test const generic is static`() = checkErrors("""
        #![feature(const_generics)]

        static S: usize = 42;

        fn foo<const C: usize>() {}

        fn main() {
            foo::<{ <error descr="Constant type parameter `C` cannot refer to static `S` [E0013]">S</error> }>();
        }
    """)

    fun `test const array expression length is static`() = checkErrors("""
        static S: usize = 3;

        fn main() {
            [isize; <error descr="Array size cannot refer to static `S` [E0013]">S</error>];
        }
    """)

}
