/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.junit.ComparisonFailure
import org.rust.ide.inspections.RsExperimentalChecksInspection
import org.rust.ide.inspections.RsInspectionsTestBase

// Typecheck errors are currently shown via disabled by default inspection,
// but should be shown via error annotator when will be complete
class RsTypeCheckTest : RsInspectionsTestBase(RsExperimentalChecksInspection()) {
    fun `test type mismatch E0308 primitive`() = checkByText("""
        fn main () {
            let _: u8 = <error>1u16</error>;
        }
    """)

    fun `test type mismatch E0308 coerce ptr mutability`() = checkByText("""
        fn fn_const(p: *const u8) { }
        fn fn_mut(p: *mut u8) { }

        fn main () {
            let mut ptr_const: *const u8;
            let mut ptr_mut: *mut u8;

            fn_const(ptr_const);
            fn_const(ptr_mut);
            fn_mut(<error>ptr_const</error>);
            fn_mut(ptr_mut);

            ptr_const = ptr_mut;
            ptr_mut = <error>ptr_const</error>;
        }
    """)

    // TODO In TypeInference.coerceResolved() we currently ignore type errors when references are involved
    fun `test type mismatch E0308 coerce reference to ptr`() = expect<ComparisonFailure> {
        checkByText("""
        fn fn_const(p: *const u8) { }
        fn fn_mut(p: *mut u8) { }

        fn main () {
            let const_u8 = &1u8;
            let mut_u8 = &mut 1u8;
            fn_const(const_u8);
            fn_const(mut_u8);
            fn_mut(<error>const_u8</error>);
            fn_mut(mut_u8);
        }
    """)
    }

    fun `test type mismatch E0308 struct`() = checkByText("""
        struct X; struct Y;
        fn main () {
            let _: X = <error>Y</error>;
        }
    """)

    fun `test type mismatch E0308 tuple`() = checkByText("""
        fn main () {
            let _: (u8, ) = (<error>1u16</error>, );
        }
    """)

    // TODO error should be more local
    fun `test type mismatch E0308 array`() = checkByText("""
        fn main () {
            let _: [u8; 1] = <error>[1u16]</error>;
        }
    """)

    fun `test type mismatch E0308 array size`() = checkByText("""
        fn main () {
            let _: [u8; 1] = <error>[1, 2]</error>;
        }
    """)

    fun `test type mismatch E0308 struct field`() = checkByText("""
        struct S { f: u8 }
        fn main () {
            S { f: <error>1u16</error> };
        }
    """)

    fun `test type mismatch E0308 function parameter`() = checkByText("""
        fn foo(_: u8) {}
        fn main () {
            foo(<error>1u16</error>)
        }
    """)

    fun `test type mismatch E0308 unconstrained integer`() = checkByText("""
        struct S;
        fn main () {
            let mut a = 0;
            a = <error>S</error>;
        }
    """)

    // issue #1753
    fun `test no type mismatch E0308 for multiple impls of the same trait`() = checkByText("""
        pub trait From<T> { fn from(_: T) -> Self; }
        struct A; struct B; struct C;
        impl From<B> for A { fn from(_: B) -> A { unimplemented!() } }
        impl From<C> for A { fn from(_: C) -> A { unimplemented!() } }
        fn main() {
            A::from(C);
        }
    """)

    // issue #1790
    fun `test not type mismatch E0308 with unknown size array`() = checkByText("""
        struct Foo {
            v: [usize; COUNT]
        }

        fn main() {
            let x = Foo { v: [10, 20] };
        }
    """)
}
