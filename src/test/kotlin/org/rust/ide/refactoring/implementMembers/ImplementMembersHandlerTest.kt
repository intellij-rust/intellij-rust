/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.implementMembers

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.MinRustcVersion
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
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
                    unimplemented!()
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
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f2()", true, false),
        ImplementMemberSelection("f3()", false, false),
        ImplementMemberSelection("f4()", false, true)
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
                <selection>unimplemented!()</selection>
            }

            fn f4() {
                unimplemented!()
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
        ImplementMemberSelection("f() -> (R, R)", true, true)
    ), """
        use a::{T, R};
        mod a {
            pub struct R;
            pub trait T {
                fn f() -> (R, R);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, R) {
                <selection>unimplemented!()</selection>
            }
        }
    """)

    fun `test import unresolved type aliases`() = doTest("""
        use a::T;
        mod a {
            pub struct R;
            pub type U = R;
            pub trait T {
                fn f() -> (R, U);
            }
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, U)", true, true)
    ), """
        use a::{T, R, U};
        mod a {
            pub struct R;
            pub type U = R;
            pub trait T {
                fn f() -> (R, U);
            }
        }
        struct S;
        impl T for S {
            fn f() -> (R, U) {
                <selection>unimplemented!()</selection>
            }
        }
    """)

    fun `test support type aliases`() = doTest("""
        pub struct R;
        pub type U = R;
        pub trait T {
            fn f() -> (R, U);
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f() -> (R, U)", true, true)
    ), """
        pub struct R;
        pub type U = R;
        pub trait T {
            fn f() -> (R, U);
        }
        struct S;
        impl T for S {
            fn f() -> (R, U) {
                <selection>unimplemented!()</selection>
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
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f2()", true, false),
        ImplementMemberSelection("f3()", false, false),
        ImplementMemberSelection("f4()", false, true)
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
                <selection>unimplemented!()</selection>
            }

            unsafe fn f4() {
                unimplemented!()
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
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f2()", true, false),
        ImplementMemberSelection("f3()", false, false),
        ImplementMemberSelection("f4()", false, true)
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
                <selection>unimplemented!()</selection>
            }

            async fn f4() {
                unimplemented!()
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
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(a: i8, b: i16, c: i32, d: i64)", true),
        ImplementMemberSelection("f2(a: (i32, u32))", true),
        ImplementMemberSelection("f3(u32, u64)", true),
        ImplementMemberSelection("f4() -> bool", true),
        ImplementMemberSelection("f5(a: f64, b: bool) -> (i8, u8)", true)
    ), """
        trait T {
            fn f1(a: i8, b: i16, c: i32, d: i64);
            fn f2(a: (i32, u32));
            fn f3(u32, u64);
            fn f4() -> bool;
            fn f5(a: f64, b: bool) -> (i8, u8);
        }
        struct S;
        impl T for S {
            fn f1(a: i8, b: i16, c: i32, d: i64) {
                <selection>unimplemented!()</selection>
            }

            fn f2(a: (i32, u32)) {
                unimplemented!()
            }

            fn f3(_: u32, _: u64) {
                unimplemented!()
            }

            fn f4() -> bool {
                unimplemented!()
            }

            fn f5(a: f64, b: bool) -> (i8, u8) {
                unimplemented!()
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
        ImplementMemberSelection("T1", true, true),
        ImplementMemberSelection("T2", true, false),
        ImplementMemberSelection("T3", false, false),
        ImplementMemberSelection("T4", false, true)
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
        ImplementMemberSelection("C1: i32", true, true),
        ImplementMemberSelection("C2: f64", true, false),
        ImplementMemberSelection("C3: &'static str", false, false),
        ImplementMemberSelection("C4: &'static str", false, true)
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
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
            }

            const C1: u8 = 0;

            fn f2(_: u16) -> u16 {
                unimplemented!()
            }

            const C2: u16 = 0;

            fn f3(_: u32) -> u32 {
                unimplemented!()
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
            <selection>unimplemented!()</selection>
        }

        const C1: &'b str = "";

        fn f2(_: &'b S<'b>) -> &'b S<'b> {
            unimplemented!()
        }

        const C2: &'b S<'b> = &S { x: "" };

        fn f3(_: &'b A<'a>) -> &'b A<'a> {
            unimplemented!()
        }

        const C3: &'b A<'a> = &S { x: "" };

        fn f4(_: &'b B) -> &'b B {
            unimplemented!()
        }

        const C4: &'b B = &S { x: "" };

        fn f5(_: &'b C) -> &'b C {
            unimplemented!()
        }

        const C5: &'b C = &S { x: "" };

        fn f6(_: &'b D<'b, D<'b, D<'b, S<'b>>>>) -> &'b D<'b, D<'b, D<'b, S<'b>>>> {
            unimplemented!()
        }

        const C6: &'b D<'b, D<'b, D<'b, S<'b>>>> = &D { x: &() };

        fn f7(&self) -> &str {
            unimplemented!()
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
                <selection>unimplemented!()</selection>
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
                unimplemented!()
            }

            const C1: S<1> = S;

            fn f2(_: S<{}>) -> S<{}> {
                unimplemented!()
            }

            const C2: S<{}> = S;

            fn f3(_: [i32; 1]) -> [i32; 1] {
                unimplemented!()
            }

            const C3: [i32; 1] = [];

            fn f4(_: [i32; {}]) -> [i32; {}] {
                unimplemented!()
            }

            const C4: [i32; {}] = [];
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
                unimplemented!()
            }

            const C1: S<{ K }> = S;

            fn f2(_: [i32; K]) -> [i32; K] {
                unimplemented!()
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
        ImplementMemberSelection("f2()", true, true),
        ImplementMemberSelection("f3()", false, false)
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
                <selection>unimplemented!()</selection>
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
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f3()", false, false)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() {
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
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
                unimplemented!()
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
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
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
                <selection>unimplemented!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Fn type`() = doTest("""
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self) -> Box<Fn(i32, i32) -> i32>;
        }

        impl Baz for Foo {
            /*caret*/
        }
    """, listOf(ImplementMemberSelection("baz(&self) -> Box<Fn(i32, i32) -> i32>", true, isSelected = true)), """
        struct Foo;
        struct Bar;
        trait Baz {
            fn baz(&self) -> Box<Fn(i32, i32) -> i32>;
        }

        impl Baz for Foo {
            fn baz(&self) -> Box<Fn(i32, i32) -> i32> {
                unimplemented!()
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
                <selection>unimplemented!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    @MinRustcVersion("1.33.0")
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
                <selection>unimplemented!()</selection>
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    @MinRustcVersion("1.33.0")
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
                <selection>unimplemented!()</selection>
            }
        }
    """)

    private data class ImplementMemberSelection(val member: String, val byDefault: Boolean, val isSelected: Boolean = byDefault)

    private fun doTest(@Language("Rust") code: String,
                       chooser: List<ImplementMemberSelection>,
                       @Language("Rust") expected: String) {

        checkByText(code.trimIndent(), expected.trimIndent()) {
            withMockTraitMemberChooser({ _, all, selectedByDefault ->
                TestCase.assertEquals(all.map { it.formattedText() }, chooser.map { it.member })
                TestCase.assertEquals(selectedByDefault.map { it.formattedText() }, chooser.filter { it.byDefault }.map { it.member })
                extractSelected(all, chooser)
            }) {
                myFixture.performEditorAction("ImplementMethods")
            }
        }
    }

    private fun extractSelected(all: List<RsTraitMemberChooserMember>, chooser: List<ImplementMemberSelection>): List<RsTraitMemberChooserMember> {
        val selected = chooser.filter { it.isSelected }.map { it.member }
        return all.filter { selected.contains(it.formattedText()) }
    }

}
