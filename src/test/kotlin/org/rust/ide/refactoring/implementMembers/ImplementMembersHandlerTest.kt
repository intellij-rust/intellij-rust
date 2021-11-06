/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.implementMembers

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2018
import org.rust.ide.inspections.RsTraitImplementationInspection

class ImplementMembersHandlerTest : RsTestBase() {
    fun `test available via override shortcut`() = invokeVia {
        myFixture.performEditorAction("ImplementMethods")
    }

    fun `test available via quick fix`() {
        myFixture.enableInspections(RsTraitImplementationInspection())
        invokeVia {
            val action = myFixture.findSingleIntention("Implement members")
            myFixture.launchAction(action)
        }
    }

    private fun invokeVia(actionInvoker: () -> Unit) {
        checkByText("""
            trait T { fn f1(); }
            struct S;
            impl T /*caret*/for S {}
        """, """
            trait T { fn f1(); }
            struct S;
            impl T for S {
                fn f1() {
                    todo!()
                }
            }
        """) {
            withMockTraitMemberChooser({ _, all, _ -> all }) {
                actionInvoker()
            }
        }
    }

    fun `test not available outside of impl`() {
        InlineFile("""
            trait T { fn f1(); }
            struct /*caret*/S;
            impl T for S {}
        """)
        ImplementMembersMarks.noImplInHandler.checkHit {
            val presentation = myFixture.testAction(ActionManagerEx.getInstanceEx().getAction("ImplementMethods"))
            check(!presentation.isEnabled)
        }
        check(myFixture.filterAvailableIntentions("Implement members").isEmpty())
    }

    fun `test implement methods`() = doTest("""
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
            fn f4() {}
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", byDefault = true, isSelected = true),
        ImplementMemberSelection("f2()", byDefault = true, isSelected = false),
        ImplementMemberSelection("f3()", byDefault = false, isSelected = false),
        ImplementMemberSelection("f4()", byDefault = false, isSelected = true)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
            fn f4() {}
        }
        struct S;
        impl T for S {
            fn f1() {
                <selection>todo!()</selection>
            }

            fn f4() {
                todo!()
            }
        }
    """)

    fun `test import unresolved types`() = doTest("""
        use a::T;
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::{R, T};
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, R) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test import unresolved types 2`() = doTest("""
        use a::T;
        mod a {
            mod private {
                pub struct R;
            }
            pub use private::R;
            pub trait T {
                fn f() -> (private::R, private::R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (private::R, private::R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use crate::a::R;

        mod a {
            mod private {
                pub struct R;
            }
            pub use private::R;
            pub trait T {
                fn f() -> (private::R, private::R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, R) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test import unresolved types inside type qualifier`() = doTest("""
        use a::T;
        mod a {
            pub struct R;
            pub trait WithAssoc { type Item; }
            impl WithAssoc for R {
                type Item = ();
            }
            pub trait T {
                fn f(a: <R as WithAssoc>::Item);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f(a: <R as WithAssoc>::Item)", byDefault = true, isSelected = true)
    ), """
        use a::{R, T, WithAssoc};
        mod a {
            pub struct R;
            pub trait WithAssoc { type Item; }
            impl WithAssoc for R {
                type Item = ();
            }
            pub trait T {
                fn f(a: <R as WithAssoc>::Item);
            }
        }
        struct S;
        impl T for S {
            fn f(a: <R as WithAssoc>::Item) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test import unresolved type aliases`() = doTest("""
        use a::T;
        mod a {
            pub struct R;
            pub type U = R;
            pub type V = i32;
            pub trait T {
                fn f() -> (R, U, V);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, U, V)", byDefault = true, isSelected = true)
    ), """
        use a::{R, T, U, V};
        mod a {
            pub struct R;
            pub type U = R;
            pub type V = i32;
            pub trait T {
                fn f() -> (R, U, V);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, U, V) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test don't import type alias inner type`() = doTest("""
        use a::T;
        mod a {
            pub struct A;
            pub struct B;
            pub struct R<T1, T2>(T1, T2);
            pub type U<P> = R<P, B>;
            pub trait T<P> {
                fn f() -> U<P>;
            }
        }
        struct S;
        impl T<i32> for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> U<P>", byDefault = true, isSelected = true)
    ), """
        use a::{T, U};
        mod a {
            pub struct A;
            pub struct B;
            pub struct R<T1, T2>(T1, T2);
            pub type U<P> = R<P, B>;
            pub trait T<P> {
                fn f() -> U<P>;
            }
        }
        struct S;
        impl T<i32> for S {
            fn f() -> U<i32> {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test don't import a type if it is already in the scope with a different name`() = doTest("""
        use a::T;
        use a::R as U;
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use a::R as U;
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (U, U) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test import unresolved trait bounds`() = doTest("""
        use a::T;
        mod a {
            pub trait Bound1 {}
            pub trait Bound2 {}
            pub trait T {
                fn f<A: Bound1>() where A: Bound2;
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f<A: Bound1>() where A: Bound2", byDefault = true, isSelected = true)
    ), """
        use a::{Bound1, Bound2, T};
        mod a {
            pub trait Bound1 {}
            pub trait Bound2 {}
            pub trait T {
                fn f<A: Bound1>() where A: Bound2;
            }
        }
        struct S;
        impl T for S {
            fn f<A: Bound1>() where A: Bound2 {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test import unresolved constant`() = doTest("""
        use a::T;
        mod a {
            pub const C: usize = 1;
            pub trait T {
                fn f() -> [u8; C];
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> [u8; C]", byDefault = true, isSelected = true)
    ), """
        use a::{C, T};
        mod a {
            pub const C: usize = 1;
            pub trait T {
                fn f() -> [u8; C];
            }
        }
        struct S;
        impl T for S {
            fn f() -> [u8; C] {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test don't import a constant if it is already in the scope with a different name`() = doTest("""
        use a::T;
        use a::C as D;
        mod a {
            pub const C: usize = 1;
            pub trait T {
                fn f() -> [u8; C];
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> [u8; C]", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use a::C as D;
        mod a {
            pub const C: usize = 1;
            pub trait T {
                fn f() -> [u8; C];
            }
        }
        struct S;
        impl T for S {
            fn f() -> [u8; D] {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test use absolute path in the case of name conflict`() = doTest("""
        use a::T;
        struct R;
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        struct R;
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (a::R, a::R) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test use relative path in the case of name conflict if intermediate mod is imported`() = doTest("""
        use a::T;
        use a::b;
        struct R;
        mod a {
            pub mod b {
                pub struct R;
            }
            use b::R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use a::b;
        struct R;
        mod a {
            pub mod b {
                pub struct R;
            }
            use b::R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (b::R, b::R) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test use relative path in the case of name conflict if intermediate mod is imported (aliased)`() = doTest("""
        use a::T;
        use a::b as c;
        struct R;
        mod a {
            pub mod b {
                pub struct R;
            }
            use b::R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use a::b as c;
        struct R;
        mod a {
            pub mod b {
                pub struct R;
            }
            use b::R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (c::R, c::R) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test use absolute path in the case of name conflict (name conflict is created during importing)`() = doTest("""
        use a::T;
        use crate::a::foo::R;
        mod a {
            pub mod foo { pub struct R; }
            pub mod bar { pub struct R; }
            pub trait T {
                fn f() -> (foo::R, bar::R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (foo::R, bar::R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        use crate::a::foo::R;
        mod a {
            pub mod foo { pub struct R; }
            pub mod bar { pub struct R; }
            pub trait T {
                fn f() -> (foo::R, bar::R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, a::bar::R) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockEdition(EDITION_2018)
    fun `test use fully qualified path if cannot import an item`() = doTest("""
        use a::T;
        mod a {
            /*private*/ struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, R)", byDefault = true, isSelected = true)
    ), """
        use a::T;
        mod a {
            /*private*/ struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (crate::a::R, crate::a::R) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support type aliases`() = doTest("""
        pub struct R;
        pub type U = R;
        pub type V = i32;
        pub trait T {
            fn f() -> (R, U, V);
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, U, V)", byDefault = true, isSelected = true)
    ), """
        pub struct R;
        pub type U = R;
        pub type V = i32;
        pub trait T {
            fn f() -> (R, U, V);
        }
        struct S;
        impl T for S {
            fn f() -> (R, U, V) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support macro type`() = doTest("""
        pub trait T {
            fn f() -> foo!();
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> foo!()", byDefault = true, isSelected = true)
    ), """
        pub trait T {
            fn f() -> foo!();
        }
        struct S;
        impl T for S {
            fn f() -> foo!() {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support extern keyword`() = doTest("""
        trait T {
            fn call(handler: extern fn(flag: bool));
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("call(handler: extern fn(flag: bool))", byDefault = true, isSelected = true)
    ), """
        trait T {
            fn call(handler: extern fn(flag: bool));
        }
        struct S;
        impl T for S {
            fn call(handler: extern fn(bool)) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support extern keyword 2`() = doTest("""
        trait T<X, Y, Z> {
            fn call(z: Z, x: X, y: Y);
        }
        struct S;
        impl T<fn(), extern fn(), fn(bool)> for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("call(z: Z, x: X, y: Y)", byDefault = true, isSelected = true)
    ), """
        trait T<X, Y, Z> {
            fn call(z: Z, x: X, y: Y);
        }
        struct S;
        impl T<fn(), extern fn(), fn(bool)> for S {
            fn call(z: fn(bool), x: fn(), y: extern fn()) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support extern keyword 3`() = doTest("""
        trait T<P = extern fn()> {
            fn call(handler: P);
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("call(handler: P)", byDefault = true, isSelected = true)
    ), """
        trait T<P = extern fn()> {
            fn call(handler: P);
        }
        struct S;
        impl T for S {
            fn call(handler: extern fn()) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test support extern keyword 4`() = doTest("""
        trait T<P = extern fn()> {
            fn call(handler: P);
        }
        struct S;
        impl T<extern fn(bool)> for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("call(handler: P)", byDefault = true, isSelected = true)
    ), """
        trait T<P = extern fn()> {
            fn call(handler: P);
        }
        struct S;
        impl T<extern fn(bool)> for S {
            fn call(handler: extern fn(bool)) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test implement unsafe methods`() = doTest("""
        trait T {
            unsafe fn f1();
            unsafe fn f2();
            unsafe fn f3() {}
            unsafe fn f4() {}
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", byDefault = true, isSelected = true),
        ImplementMemberSelection("f2()", byDefault = true, isSelected = false),
        ImplementMemberSelection("f3()", byDefault = false, isSelected = false),
        ImplementMemberSelection("f4()", byDefault = false, isSelected = true)
    ), """
        trait T {
            unsafe fn f1();
            unsafe fn f2();
            unsafe fn f3() {}
            unsafe fn f4() {}
        }
        struct S;
        impl T for S {
            unsafe fn f1() {
                <selection>todo!()</selection>
            }

            unsafe fn f4() {
                todo!()
            }
        }
    """)

    fun `test implement async methods`() = doTest("""
        trait T {
            async fn f1();
            async fn f2();
            async fn f3() {}
            async fn f4() {}
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", byDefault = true, isSelected = true),
        ImplementMemberSelection("f2()", byDefault = true, isSelected = false),
        ImplementMemberSelection("f3()", byDefault = false, isSelected = false),
        ImplementMemberSelection("f4()", byDefault = false, isSelected = true)
    ), """
        trait T {
            async fn f1();
            async fn f2();
            async fn f3() {}
            async fn f4() {}
        }
        struct S;
        impl T for S {
            async fn f1() {
                <selection>todo!()</selection>
            }

            async fn f4() {
                todo!()
            }
        }
    """)

    fun `test implement more methods`() = doTest("""
        trait T {
            fn f1(a: i8, b: i16, c: i32, d: i64);
            fn f2(a: (i32, u32));
            fn f3(u32, u64);
            fn f4() -> bool;
            fn f5(a: f64, b: bool) -> (i8, u8);
            fn f6(a: (i32,)) -> (i8,);
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(a: i8, b: i16, c: i32, d: i64)", true),
        ImplementMemberSelection("f2(a: (i32, u32))", true),
        ImplementMemberSelection("f3(u32, u64)", true),
        ImplementMemberSelection("f4() -> bool", true),
        ImplementMemberSelection("f5(a: f64, b: bool) -> (i8, u8)", true),
        ImplementMemberSelection("f6(a: (i32,)) -> (i8,)", true),
    ), """
        trait T {
            fn f1(a: i8, b: i16, c: i32, d: i64);
            fn f2(a: (i32, u32));
            fn f3(u32, u64);
            fn f4() -> bool;
            fn f5(a: f64, b: bool) -> (i8, u8);
            fn f6(a: (i32,)) -> (i8,);
        }
        struct S;
        impl T for S {
            fn f1(a: i8, b: i16, c: i32, d: i64) {
                <selection>todo!()</selection>
            }

            fn f2(a: (i32, u32)) {
                todo!()
            }

            fn f3(_: u32, _: u64) {
                todo!()
            }

            fn f4() -> bool {
                todo!()
            }

            fn f5(a: f64, b: bool) -> (i8, u8) {
                todo!()
            }

            fn f6(a: (i32, )) -> (i8, ) {
                todo!()
            }
        }
    """)

    fun `test implement types`() = doTest("""
        trait T {
            type T1;
            type T2;
            type T3 = i32;
            type T4 = f64;
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("T1", byDefault = true, isSelected = true),
        ImplementMemberSelection("T2", byDefault = true, isSelected = false),
        ImplementMemberSelection("T3", byDefault = false, isSelected = false),
        ImplementMemberSelection("T4", byDefault = false, isSelected = true)
    ), """
        trait T {
            type T1;
            type T2;
            type T3 = i32;
            type T4 = f64;
        }
        struct S;
        impl T for S {
            type T1 = <selection>()</selection>;
            type T4 = ();
        }
    """)

    fun `test implement constants`() = doTest("""
        trait T {
            const C1: i32;
            const C2: f64;
            const C3: &'static str = "foo";
            const C4: &'static str = "bar";
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("C1: i32", byDefault = true, isSelected = true),
        ImplementMemberSelection("C2: f64", byDefault = true, isSelected = false),
        ImplementMemberSelection("C3: &'static str", byDefault = false, isSelected = false),
        ImplementMemberSelection("C4: &'static str", byDefault = false, isSelected = true)
    ), """
        trait T {
            const C1: i32;
            const C2: f64;
            const C3: &'static str = "foo";
            const C4: &'static str = "bar";
        }
        struct S;
        impl T for S {
            const C1: i32 = <selection>0</selection>;
            const C4: &'static str = "";
        }
    """)

    fun `test implement all`() = doTest("""
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
            const C2: f64 = 4.2;
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", true),
        ImplementMemberSelection("T1", true),
        ImplementMemberSelection("C1: i32", true),
        ImplementMemberSelection("f2()", false),
        ImplementMemberSelection("T2", false),
        ImplementMemberSelection("C2: f64", false)
    ), """
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
            const C2: f64 = 4.2;
        }
        struct S;
        impl T for S {
            fn f1() {
                <selection>todo!()</selection>
            }

            type T1 = ();
            const C1: i32 = 0;
        }
    """)

    fun `test implement generic trait`() = doTest("""
        trait T<A, B = i16, C = u32> {
            fn f1(_: A) -> A;
            const C1: A;
            fn f2(_: B) -> B;
            const C2: B;
            fn f3(_: C) -> C;
            const C3: C;
        }
        struct S;
        impl T<u8, u16> for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(_: A) -> A", true),
        ImplementMemberSelection("C1: A", true),
        ImplementMemberSelection("f2(_: B) -> B", true),
        ImplementMemberSelection("C2: B", true),
        ImplementMemberSelection("f3(_: C) -> C", true),
        ImplementMemberSelection("C3: C", true)
    ), """
        trait T<A, B = i16, C = u32> {
            fn f1(_: A) -> A;
            const C1: A;
            fn f2(_: B) -> B;
            const C2: B;
            fn f3(_: C) -> C;
            const C3: C;
        }
        struct S;
        impl T<u8, u16> for S {
            fn f1(_: u8) -> u8 {
                <selection>todo!()</selection>
            }

            const C1: u8 = 0;

            fn f2(_: u16) -> u16 {
                todo!()
            }

            const C2: u16 = 0;

            fn f3(_: u32) -> u32 {
                todo!()
            }

            const C3: u32 = 0;
        }
    """)

    fun `test implement generic trait with lifetimes`() = doTest("""
        struct S<'a> { x: &'a str }
        struct D<'a, T> { x: &'a T }
        type A<'a> = S<'a>;
        type B = S<'static>;
        type C = S<'unknown>;
        trait T<'a> {
            fn f1(&'a self) -> &'a str;
            const C1: &'a str;
            fn f2(_: &'a S<'a>) -> &'a S<'a>;
            const C2: &'a S<'a>;
            fn f3(_: &'a A<'a>) -> &'a A<'a>;
            const C3: &'a A<'a>;
            fn f4(_: &'a B) -> &'a B;
            const C4: &'a B;
            fn f5(_: &'a C) -> &'a C;
            const C5: &'a C;
            fn f6(_: &'a D<'a, D<'a, D<'a, S<'a>>>>) -> &'a D<'a, D<'a, D<'a, S<'a>>>>;
            const C6: &'a D<'a, D<'a, D<'a, S<'a>>>>;
            fn f7(&self) -> &str;
        }
        impl<'b> T<'b> for S<'b> {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(&'a self) -> &'a str", true),
        ImplementMemberSelection("C1: &'a str", true),
        ImplementMemberSelection("f2(_: &'a S<'a>) -> &'a S<'a>", true),
        ImplementMemberSelection("C2: &'a S<'a>", true),
        ImplementMemberSelection("f3(_: &'a A<'a>) -> &'a A<'a>", true),
        ImplementMemberSelection("C3: &'a A<'a>", true),
        ImplementMemberSelection("f4(_: &'a B) -> &'a B", true),
        ImplementMemberSelection("C4: &'a B", true),
        ImplementMemberSelection("f5(_: &'a C) -> &'a C", true),
        ImplementMemberSelection("C5: &'a C", true),
        ImplementMemberSelection("f6(_: &'a D<'a, D<'a, D<'a, S<'a>>>>) -> &'a D<'a, D<'a, D<'a, S<'a>>>>", true),
        ImplementMemberSelection("C6: &'a D<'a, D<'a, D<'a, S<'a>>>>", true),
        ImplementMemberSelection("f7(&self) -> &str", true)
    ), """
    struct S<'a> { x: &'a str }
    struct D<'a, T> { x: &'a T }
    type A<'a> = S<'a>;
    type B = S<'static>;
    type C = S<'unknown>;
    trait T<'a> {
        fn f1(&'a self) -> &'a str;
        const C1: &'a str;
        fn f2(_: &'a S<'a>) -> &'a S<'a>;
        const C2: &'a S<'a>;
        fn f3(_: &'a A<'a>) -> &'a A<'a>;
        const C3: &'a A<'a>;
        fn f4(_: &'a B) -> &'a B;
        const C4: &'a B;
        fn f5(_: &'a C) -> &'a C;
        const C5: &'a C;
        fn f6(_: &'a D<'a, D<'a, D<'a, S<'a>>>>) -> &'a D<'a, D<'a, D<'a, S<'a>>>>;
        const C6: &'a D<'a, D<'a, D<'a, S<'a>>>>;
        fn f7(&self) -> &str;
    }
    impl<'b> T<'b> for S<'b> {
        fn f1(&'b self) -> &'b str {
            <selection>todo!()</selection>
        }

        const C1: &'b str = "";

        fn f2(_: &'b S<'b>) -> &'b S<'b> {
            todo!()
        }

        const C2: &'b S<'b> = &S { x: "" };

        fn f3(_: &'b A<'b>) -> &'b A<'b> {
            todo!()
        }

        const C3: &'b A<'b> = &S { x: "" };

        fn f4(_: &'b B) -> &'b B {
            todo!()
        }

        const C4: &'b B = &S { x: "" };

        fn f5(_: &'b C) -> &'b C {
            todo!()
        }

        const C5: &'b C = &S { x: "" };

        fn f6(_: &'b D<'b, D<'b, D<'b, S<'b>>>>) -> &'b D<'b, D<'b, D<'b, S<'b>>>> {
            todo!()
        }

        const C6: &'b D<'b, D<'b, D<'b, S<'b>>>> = &D { x: &() };

        fn f7(&self) -> &str {
            todo!()
        }
    }
    """)

    fun `test implement items with raw identifiers`() = doTest("""
        trait T {
            fn r#type();
            type r#const;
            const r#pub: i32;
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("type()", true),
        ImplementMemberSelection("const", true),
        ImplementMemberSelection("pub: i32", true)
    ), """
        trait T {
            fn r#type();
            type r#const;
            const r#pub: i32;
        }
        struct S;
        impl T for S {
            fn r#type() {
                <selection>todo!()</selection>
            }

            type r#const = ();
            const r#pub: i32 = 0;
        }
    """)

    fun `test implement generic trait with consts 1`() = doTest("""
        struct S<const N: usize>;
        trait T<const M: usize> {
            fn f1(_: S<{ M }>) -> S<{ M }>;
            const C1: S<{ M }>;
            fn f2(_: S<{ UNKNOWN }>) -> S<{ UNKNOWN }>;
            const C2: S<{ UNKNOWN }>;
            fn f3(_: [i32; M]) -> [i32; M];
            const C3: [i32; M];
            fn f4(_: [i32; UNKNOWN]) -> [i32; UNKNOWN];
            const C4: [i32; UNKNOWN];
        }
        impl T<1> for S<1> {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(_: S<{ M }>) -> S<{ M }>", true),
        ImplementMemberSelection("C1: S<{ M }>", true),
        ImplementMemberSelection("f2(_: S<{ UNKNOWN }>) -> S<{ UNKNOWN }>", true),
        ImplementMemberSelection("C2: S<{ UNKNOWN }>", true),
        ImplementMemberSelection("f3(_: [i32; M]) -> [i32; M]", true),
        ImplementMemberSelection("C3: [i32; M]", true),
        ImplementMemberSelection("f4(_: [i32; UNKNOWN]) -> [i32; UNKNOWN]", true),
        ImplementMemberSelection("C4: [i32; UNKNOWN]", true)
    ), """
        struct S<const N: usize>;
        trait T<const M: usize> {
            fn f1(_: S<{ M }>) -> S<{ M }>;
            const C1: S<{ M }>;
            fn f2(_: S<{ UNKNOWN }>) -> S<{ UNKNOWN }>;
            const C2: S<{ UNKNOWN }>;
            fn f3(_: [i32; M]) -> [i32; M];
            const C3: [i32; M];
            fn f4(_: [i32; UNKNOWN]) -> [i32; UNKNOWN];
            const C4: [i32; UNKNOWN];
        }
        impl T<1> for S<1> {
            fn f1(_: S<1>) -> S<1> {
                todo!()
            }

            const C1: S<1> = S;

            fn f2(_: S<{ UNKNOWN }>) -> S<{ UNKNOWN }> {
                todo!()
            }

            const C2: S<{ UNKNOWN }> = S;

            fn f3(_: [i32; 1]) -> [i32; 1] {
                todo!()
            }

            const C3: [i32; 1] = [];

            fn f4(_: [i32; UNKNOWN]) -> [i32; UNKNOWN] {
                todo!()
            }

            const C4: [i32; UNKNOWN] = [];
        }
    """)

    fun `test implement generic trait with consts 2`() = doTest("""
        struct S<const N: usize>;
        trait T<const M: usize> {
            fn f1(_: S<{ M }>) -> S<{ M }>;
            const C1: S<{ M }>;
            fn f2(_: [i32; M]) -> [i32; M];
            const C2: [i32; M];
        }
        impl <const K: usize> T<{ K }> for S<{ K }> {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(_: S<{ M }>) -> S<{ M }>", true),
        ImplementMemberSelection("C1: S<{ M }>", true),
        ImplementMemberSelection("f2(_: [i32; M]) -> [i32; M]", true),
        ImplementMemberSelection("C2: [i32; M]", true)
    ), """
        struct S<const N: usize>;
        trait T<const M: usize> {
            fn f1(_: S<{ M }>) -> S<{ M }>;
            const C1: S<{ M }>;
            fn f2(_: [i32; M]) -> [i32; M];
            const C2: [i32; M];
        }
        impl <const K: usize> T<{ K }> for S<{ K }> {
            fn f1(_: S<{ K }>) -> S<{ K }> {
                todo!()
            }

            const C1: S<{ K }> = S;

            fn f2(_: [i32; K]) -> [i32; K] {
                todo!()
            }

            const C2: [i32; K] = [];
        }
    """)

    fun `test do not implement methods already present`() = doTest("""
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() { }
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("f2()", byDefault = true, isSelected = true),
        ImplementMemberSelection("f3()", byDefault = false, isSelected = false)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() { }

            fn f2() {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test do not implement methods already present #2`() = doTest("""
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f2() { }/*caret*/
        }
    """, listOf(
        ImplementMemberSelection("f1()", byDefault = true, isSelected = true),
        ImplementMemberSelection("f3()", byDefault = false, isSelected = false)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() {
                <selection>todo!()</selection>
            }

            fn f2() { }
        }
    """)

    fun `test honours the order of members in the definition if it's already honoured`() = doTest("""
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
        }
        struct S;
        impl T for S {
            type T1 = u32;
            fn f2() {}
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("f1()", true, isSelected = true),
        ImplementMemberSelection("C1: i32", true, isSelected = true),
        ImplementMemberSelection("f2()", false),
        ImplementMemberSelection("T2", false, isSelected = true)
    ), """
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
        }
        struct S;
        impl T for S {
            fn f1() {
                <selection>todo!()</selection>
            }

            type T1 = u32;
            const C1: i32 = 0;

            fn f2() {}

            type T2 = ();
        }
    """)

    fun `test appends new members at the end in the right order if the order isn't honoured`() = doTest("""
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
        }
        struct S;
        impl T for S {
            fn f2() {}
            type T1 = u32;
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("f1()", true, isSelected = true),
        ImplementMemberSelection("C1: i32", true, isSelected = true),
        ImplementMemberSelection("f2()", false),
        ImplementMemberSelection("T2", false, isSelected = true)
    ), """
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
        }
        struct S;
        impl T for S {
            fn f2() {}
            type T1 = u32;

            fn f1() {
                <selection>todo!()</selection>
            }

            const C1: i32 = 0;
            type T2 = ();
        }
    """)

    fun `test works properly when a type alias shares the name with another member`() = doTest("""
        trait T {
            fn x();
            type y;
            const z: i32;
            fn y();
        }
        struct S;
        impl T for S {
            const z: i32 = 20;

            fn y() {}/*caret*/
        }
    """, listOf(
        ImplementMemberSelection("x()", true, isSelected = true),
        ImplementMemberSelection("y", true, isSelected = true)
    ), """
        trait T {
            fn x();
            type y;
            const z: i32;
            fn y();
        }
        struct S;
        impl T for S {
            fn x() {
                <selection>todo!()</selection>
            }

            type y = ();

            const z: i32 = 20;

            fn y() {}
        }
    """)

    fun `test self associated type`() = doTest("""
        trait T {
            type Item;
            fn foo() -> Self::Item;
        }
        struct S;
        impl T for S {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("Item", true, isSelected = true),
        ImplementMemberSelection("foo() -> Self::Item", true, isSelected = true)
    ), """
        trait T {
            type Item;
            fn foo() -> Self::Item;
        }
        struct S;
        impl T for S {
            type Item = <selection>()</selection>;

            fn foo() -> Self::Item {
                todo!()
            }
        }
    """)

    fun `test with members defined by a macro`() = doTest("""
        macro_rules! foo {
            ($ i:ident, $ j:tt) => { fn $ i() $ j }
        }
        trait T {
            foo!(foo, ;);
            foo!(bar, ;);
            foo!(baz, ;);
        }
        struct S;
        impl T for S {
            foo!(foo, {});
            fn baz() {}/*caret*/
        }
    """, listOf(
        ImplementMemberSelection("bar ()", true, isSelected = true)
    ), """
        macro_rules! foo {
            ($ i:ident, $ j:tt) => { fn $ i() $ j }
        }
        trait T {
            foo!(foo, ;);
            foo!(bar, ;);
            foo!(baz, ;);
        }
        struct S;
        impl T for S {
            foo!(foo, {});

            fn bar() {
                <selection>todo!()</selection>
            }

            fn baz() {}
        }
    """)

    fun `test do not add lifetime in implementation`() = doTest("""
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self, bar: &mut Bar);
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(&self, bar: &mut Bar)", true, isSelected = true)), """
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self, bar: &mut Bar);
        }

        impl Baz for Foo {
            fn baz(&self, bar: &mut Bar) {
                <selection>todo!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Box self type`() = doTest("""
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: Box<Self>, bar: &mut Bar);
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(self: Box<Self>, bar: &mut Bar)", true, isSelected = true)), """
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: Box<Self>, bar: &mut Bar);
        }

        impl Baz for Foo {
            fn baz(self: Box<Self>, bar: &mut Bar) {
                <selection>todo!()</selection>
            }
        }
    """)

    fun `test trait object type`() = doTest("""
        struct Foo;
        trait A {}
        trait B {}
        trait Bar {
            fn bar(&self) -> &dyn A + B;
        }

        impl Bar for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("bar(&self) -> &dyn A + B", true, isSelected = true)), """
        struct Foo;
        trait A {}
        trait B {}
        trait Bar {
            fn bar(&self) -> &dyn A + B;
        }

        impl Bar for Foo {
            fn bar(&self) -> &dyn A + B {
                todo!()
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Fn type`() = doTest("""
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self) -> Box<dyn Fn(i32, i32) -> i32>;
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(&self) -> Box<dyn Fn(i32, i32) -> i32>", true, isSelected = true)), """
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self) -> Box<dyn Fn(i32, i32) -> i32>;
        }

        impl Baz for Foo {
            fn baz(&self) -> Box<dyn Fn(i32, i32) -> i32> {
                todo!()
            }
        }
    """)

    fun `test pointer self type`() = doTest("""
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: *const Self, bar: &mut Bar);
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(self: *const Self, bar: &mut Bar)", true, isSelected = true)), """
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: *const Self, bar: &mut Bar);
        }

        impl Baz for Foo {
            fn baz(self: *const Self, bar: &mut Bar) {
                <selection>todo!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Pin self type`() = doTest("""
        use std::pin::Pin;
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: Pin<&mut Self>, bar: &mut Bar);
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(self: Pin<&mut Self>, bar: &mut Bar)", true, isSelected = true)), """
        use std::pin::Pin;
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(self: Pin<&mut Self>, bar: &mut Bar);
        }

        impl Baz for Foo {
            fn baz(self: Pin<&mut Self>, bar: &mut Bar) {
                <selection>todo!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Pin self type with lifetime`() = doTest("""
        use std::pin::Pin;
        struct Foo;
        struct Bar;
        trait Baz<'s> {
            fn baz(self: Pin<&'s mut Self>, bar: &mut Bar);
        }

        impl<'a> Baz<'a> for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(self: Pin<&'s mut Self>, bar: &mut Bar)", true, isSelected = true)), """
        use std::pin::Pin;
        struct Foo;
        struct Bar;
        trait Baz<'s> {
            fn baz(self: Pin<&'s mut Self>, bar: &mut Bar);
        }

        impl<'a> Baz<'a> for Foo {
            fn baz(self: Pin<&'a mut Self>, bar: &mut Bar) {
                <selection>todo!()</selection>
            }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test do not offer cfg-disabled items`() = doTest("""
        trait Foo {
            #[cfg(intellij_rust)]
            fn foo(&self) {}
            #[cfg(not(intellij_rust))]
            fn foo(&self);

            fn baz(&self);
        }

        struct S;
        impl Foo for S {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo(&self)", false, isSelected = true),
        ImplementMemberSelection("baz(&self)", true, isSelected = true)
    ), """
        trait Foo {
            #[cfg(intellij_rust)]
            fn foo(&self) {}
            #[cfg(not(intellij_rust))]
            fn foo(&self);

            fn baz(&self);
        }

        struct S;
        impl Foo for S {
            fn foo(&self) {
                todo!()
            }

            fn baz(&self) {
                todo!()
            }
        }
    """)

    fun `test default type params 1`() = doTest("""
        struct S<T = i32>(T);
        trait Foo {
            fn foo() -> S;
        }
        struct Bar;
        impl Foo for Bar {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo() -> S", byDefault = true)
    ), """
        struct S<T = i32>(T);
        trait Foo {
            fn foo() -> S;
        }
        struct Bar;
        impl Foo for Bar {
            fn foo() -> S {
                todo!()
            }
        }
    """)

    fun `test default type params 2`() = doTest("""
        struct S<T = i32>(T);
        trait Foo {
            fn foo() -> S<u64>;
        }
        struct Bar;
        impl Foo for Bar {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo() -> S<u64>", byDefault = true)
    ), """
        struct S<T = i32>(T);
        trait Foo {
            fn foo() -> S<u64>;
        }
        struct Bar;
        impl Foo for Bar {
            fn foo() -> S<u64> {
                todo!()
            }
        }
    """)

    fun `test default type params 3`() = doTest("""
        mod m {
            pub struct Q;
        }
        struct S<T = m::Q>(T);
        trait Foo {
            fn foo() -> S;
        }
        struct Bar;
        impl Foo for Bar {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo() -> S", byDefault = true)
    ), """
        mod m {
            pub struct Q;
        }
        struct S<T = m::Q>(T);
        trait Foo {
            fn foo() -> S;
        }
        struct Bar;
        impl Foo for Bar {
            fn foo() -> S {
                todo!()
            }
        }
    """)

    fun `test default type params 4`() = doTest("""
        struct S<T1 = i32, T2 = i32, T3 = i32>(T1, T2, T3);
        trait Foo {
            fn foo() -> S<i32, u32>;
        }
        struct Bar;
        impl Foo for Bar {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo() -> S<i32, u32>", byDefault = true)
    ), """
        struct S<T1 = i32, T2 = i32, T3 = i32>(T1, T2, T3);
        trait Foo {
            fn foo() -> S<i32, u32>;
        }
        struct Bar;
        impl Foo for Bar {
            fn foo() -> S<i32, u32> {
                todo!()
            }
        }
    """)

    fun `test default type params for type alias`() = doTest("""
        struct S<T = i32>(T);
        type A<T = u32> = S<T>;
        trait Foo {
            fn foo(_: A<i32>) -> A;
        }
        struct Bar;
        impl Foo for Bar {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo(_: A<i32>) -> A", byDefault = true)
    ), """
        struct S<T = i32>(T);
        type A<T = u32> = S<T>;
        trait Foo {
            fn foo(_: A<i32>) -> A;
        }
        struct Bar;
        impl Foo for Bar {
            fn foo(_: A<i32>) -> A {
                todo!()
            }
        }
    """)

    fun `test const type param`() = doTest("""
        trait Foo {
            fn foo<const N: usize>();
        }
        struct S;
        impl Foo for S {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo<const N: usize>()", byDefault = true)
    ), """
        trait Foo {
            fn foo<const N: usize>();
        }
        struct S;
        impl Foo for S {
            fn foo<const N: usize>() {
                todo!()
            }
        }
    """)

    fun `test type reference parentheses`() = doTest("""
        trait T {}
        trait Foo {
            fn foo() -> &(dyn T + 'static);
        }
        struct S;
        impl Foo for S {
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("foo() -> &(dyn T + 'static)", byDefault = true)
    ), """
        trait T {}
        trait Foo {
            fn foo() -> &(dyn T + 'static);
        }
        struct S;
        impl Foo for S {
            fn foo() -> &(dyn T + 'static) {
                todo!()
            }
        }
    """)

    private data class ImplementMemberSelection(val member: String, val byDefault: Boolean, val isSelected: Boolean = byDefault)

    private fun doTest(
        @Language("Rust") code: String,
        chooser: List<ImplementMemberSelection>,
        @Language("Rust") expected: String
    ) {

        checkByText(code.trimIndent(), expected.trimIndent()) {
            withMockTraitMemberChooser({ _, all, selectedByDefault ->
                assertEquals(chooser.map { it.member }, all.map { it.formattedText() })
                assertEquals(chooser.filter { it.byDefault }.map { it.member }, selectedByDefault.map { it.formattedText() })
                extractSelected(all, chooser)
            }) {
                myFixture.performEditorAction("ImplementMethods")
            }
        }
    }

    private fun extractSelected(
        all: List<RsTraitMemberChooserMember>,
        chooser: List<ImplementMemberSelection>
    ): List<RsTraitMemberChooserMember> {
        val selected = chooser.filter { it.isSelected }.map { it.member }
        return all.filter { selected.contains(it.formattedText()) }
    }

}
