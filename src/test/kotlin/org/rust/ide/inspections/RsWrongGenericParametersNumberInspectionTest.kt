/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongGenericParametersNumberInspectionTest : RsInspectionsTestBase(RsWrongGenericParametersNumberInspection::class) {
    fun `test type parameters`() = checkByText("""
        struct S;
        trait T {
            type Ty<A, B>;
            fn foo<A>();
            fn bar<A>();
        }
        impl T for S {
            type Ty<error descr="Type `Ty` has 1 type parameter but its trait declaration has 2 type parameters [E0049]"><A></error> = ();
            fn foo<error descr="Method `foo` has 2 type parameters but its trait declaration has 1 type parameter [E0049]"><A, B></error>() {}
            fn <error descr="Method `bar` has 0 type parameters but its trait declaration has 1 type parameter [E0049]">bar</error>() {}
        }
    """)

    fun `test const parameters`() = checkByText("""
        struct S;
        trait T {
            type Ty<const A: usize, const B: usize>;
            fn foo<const A: usize>();
        }
        impl T for S {
            type Ty<error descr="Type `Ty` has 1 const parameter but its trait declaration has 2 const parameters [E0049]"><const A: usize></error> = ();
            fn foo<error descr="Method `foo` has 2 const parameters but its trait declaration has 1 const parameter [E0049]"><const A: usize, const B: usize></error>() {}
        }
    """)

    fun `test type and const parameters`() = checkByText("""
        struct S;
        trait T {
            type Ty<A, B, const C: usize>;
            fn foo<A, const B: usize, const C: usize>();
        }
        impl T for S {
            type Ty<error descr="Type `Ty` has 1 type parameter but its trait declaration has 2 type parameters [E0049]"><error descr="Type `Ty` has 2 const parameters but its trait declaration has 1 const parameter [E0049]"><A, const B: usize, const C: usize></error></error> = ();
            fn foo<error descr="Method `foo` has 1 const parameter but its trait declaration has 2 const parameters [E0049]"><error descr="Method `foo` has 2 type parameters but its trait declaration has 1 type parameter [E0049]"><A, B, const C: usize></error></error>() {}
        }
    """)

    fun `test parameters naming and ordering`() = checkByText("""
        struct S;
        trait T {
            type Ty<A1, const B1: usize>;
            fn foo<const A1: usize, B1>();
        }
        impl T for S {
            type Ty<const A2: usize, B2> = ();
            fn foo<A2, const B2: usize>() {}
        }
    """)
}
