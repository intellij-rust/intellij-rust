/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTraitImplementationInspection

class AddDefinitionToTraitFixTest : RsInspectionsTestBase(RsTraitImplementationInspection::class) {

    fun `test simple method`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait {}
        impl Trait for Struct {
            fn /*error descr="Method `func` is not a member of trait `Trait` [E0407]"*//*caret*/func/*error**/() {}
        }
    """, """
        struct Struct {}
        trait Trait {
            fn func();
        }
        impl Trait for Struct {
            fn func() {}
        }
    """)

    fun `test simple assoc constant`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait {}
        impl Trait for Struct {
            const /*error descr="Const `FOO` is not a member of trait `Trait` [E0438]"*//*caret*/FOO/*error**/: i32 = 0;
        }
    """, """
        struct Struct {}
        trait Trait {
            const FOO: i32;
        }
        impl Trait for Struct {
            const FOO: i32 = 0;
        }
    """)

    fun `test simple assoc type`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait {}
        impl Trait for Struct {
            type /*error descr="Type `T` is not a member of trait `Trait` [E0437]"*//*caret*/T/*error**/ = i32;
        }
    """, """
        struct Struct {}
        trait Trait {
            type T;
        }
        impl Trait for Struct {
            type T = i32;
        }
    """)

    fun `test insert at correct position if members are in order`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait {
            fn a();
            fn b();
        }
        impl Trait for Struct {
            fn a() {}
            fn /*error descr="Method `c` is not a member of trait `Trait` [E0407]"*//*caret*/c/*error**/() {}
            fn b() {}
        }
    """, """
        struct Struct {}
        trait Trait {
            fn a();
            fn c();
            fn b();
        }
        impl Trait for Struct {
            fn a() {}
            fn c() {}
            fn b() {}
        }
    """)

    fun `test insert at the end if members are out of order`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait {
            fn a();
            fn b();
        }
        impl Trait for Struct {
            fn b() {}
            fn /*error descr="Method `c` is not a member of trait `Trait` [E0407]"*//*caret*/c/*error**/() {}
            fn a() {}
        }
    """, """
        struct Struct {}
        trait Trait {
            fn a();
            fn b();
            fn c();
        }
        impl Trait for Struct {
            fn b() {}
            fn c() {}
            fn a() {}
        }
    """)

    fun `test assoc type with generics`() = checkFixByText("Add definition to trait", """
        struct Struct {}
        trait Trait<A> {}
        impl<A> Trait<A> for Struct {
            type /*error descr="Type `T` is not a member of trait `Trait` [E0437]"*//*caret*/T/*error**/ = A;
        }
    """, """
        struct Struct {}
        trait Trait<A> {
            type T;
        }
        impl<A> Trait<A> for Struct {
            type T = A;
        }
    """)

    fun `test not available if member contains generics`() = checkFixIsUnavailable("Add definition to trait", """
        struct Struct {}
        trait Trait<A, B> {}
        impl<C> Trait<C, C> for Struct {
            // Can't substitute back from C - is it A or B?
            const /*error descr="Const `FOO` is not a member of trait `Trait` [E0438]"*//*caret*/FOO/*error**/: C = 0;
        }
    """)

    fun `test not available when implement trait with substitution`() = checkFixIsUnavailable("Add definition to trait", """
        struct Struct {}
        trait Trait<A> {
            // const FOO: A;
        }
        impl Trait<i32> for Struct {
            // Can't substitute back from i32 to A
            const /*error descr="Const `FOO` is not a member of trait `Trait` [E0438]"*//*caret*/FOO/*error**/: i32 = 0;
        }
    """)

    fun `test generics`() = checkFixByText("Add definition to trait", """
        struct Struct<'a, A, const CA: i32> { field: &'a A }
        trait Trait<'b, B, const CB: i32> {}
        impl<'c, C, const CC: i32> Trait<'c, C, CC> for Struct<'c, C, CC> {
            fn /*error descr="Method `func` is not a member of trait `Trait` [E0407]"*//*caret*/func/*error**/() {}
        }
    """, """
        struct Struct<'a, A, const CA: i32> { field: &'a A }
        trait Trait<'b, B, const CB: i32> {
            fn func();
        }
        impl<'c, C, const CC: i32> Trait<'c, C, CC> for Struct<'c, C, CC> {
            fn func() {}
        }
    """)
}
