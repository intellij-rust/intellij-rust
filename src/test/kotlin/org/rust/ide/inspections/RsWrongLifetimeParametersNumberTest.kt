/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongLifetimeParametersNumberInspectionTest : RsInspectionsTestBase(RsWrongLifetimeParametersNumberInspection::class) {

    fun `test E0106 missing lifetime in struct field`() = checkByText("""
        struct Foo<'a> {
            a: &'a str,
            b: (bool, (u8, &'a f64)),
            c: Option<Box<&'a u32>>,
            f: &'a Fn (&u32) -> &u32,
        }
        struct Bar<'a> {
            a: <error descr="Missing lifetime specifier [E0106]">&</error>str,
            b: (bool, (u8, <error>&</error>f64)),
            c: Result<Box<<error>&</error>u32>, u8>,
            f: <error>&</error>Fn (&u32) -> &u32,
        }
    """)

    fun `test E0106 missing lifetime in tuple struct field`() = checkByText("""
        struct Foo<'a> (
            &'a str,
            (bool, (u8, &'a f64)),
            &'a Fn (&u32) -> &u32);
        struct Bar<'a> (
            <error descr="Missing lifetime specifier [E0106]">&</error>str,
            (bool, (u8, <error>&</error>f64)),
            <error>&</error>Fn (&u32) -> &u32);
    """)

    fun `test E0106 missing lifetime in enum`() = checkByText("""
        enum Foo<'a> {
            A(&'a str),
            B(bool, (u8, &'a f64)),
            F(&'a Fn (&u32) -> &u32),
        }
        enum Bar<'a> {
            A(<error descr="Missing lifetime specifier [E0106]">&</error>str),
            B(bool, (u8, <error>&</error>f64)),
            F(<error>&</error>Fn (&u32) -> &u32),
        }
    """)

    fun `test E0106 missing lifetime in type alias`() = checkByText("""
        type Str = &'static str;
        type Foo<'a> = &'a Fn (&u32) -> &u32;

        type U32 = <error descr="Missing lifetime specifier [E0106]">&</error>u32;
        type Tuple = (bool, (u8, <error>&</error>f64));
        type Func = <error>&</error>Fn (&u32) -> &u32;
    """)

    fun `test E0106 missing lifetime ignores raw pointers`() = checkByText("""
        struct Foo {
            raw: *const i32   // Must not be highlighted
        }
    """)

    fun `test E0106 missing lifetime in base types`() = checkByText("""
        struct Foo1<'a>(&'a str);
        struct Foo2<'a, 'b> { a: &'a u32, b: &'b str }

        type Err1 = <error descr="Missing lifetime specifier [E0106]">Foo1</error>;
        struct Err2<'a> { a: <error>Foo2<></error> }
        enum Err3<'d> { A(&'d Box<<error>Foo1</error>> ) }
    """)

    fun `test E0106 missing lifetime in associated constant`() = checkByText("""
        trait TErr {
            const C: <error descr="Missing lifetime specifier [E0106]">&</error>str;
        }

        struct SErr;
        impl SErr {
            const C: <error descr="Missing lifetime specifier [E0106]">&</error>str = "foo";
        }

        enum EErr {}
        impl EErr {
            const C: <error descr="Missing lifetime specifier [E0106]">&</error>str = "foo";
        }

        // There should be no error for non-associated constant.
        const C: &str = "foo";

        // These should have no errors since they declare a lifetime.
        trait TOk<'a> {
            const C: &'a str;
        }

        struct SOk;
        impl <'a> SOk {
            const C: &'a str = "foo";
        }

        enum EOk {}
        impl <'a> EOk {
            const C: &'a str = "foo";
        }

    """)

    fun `test E0106 missing lifetime in function`() = checkByText("""
        fn func1() {}
        fn func2(a: &i32) -> &i32 { unimplemented!() }
        fn func3(a: &i32, b: &i32) {}
        fn func4() -> fn(&i32) { |_| {} }
        fn func5() -> impl Fn(&i32) { |_| {} }

        fn func6() -> /*error descr="Missing lifetime specifier [E0106]"*/&/*error**/i32 { unimplemented!() }
        fn func7(a: &i32, b: &i32) -> /*error descr="Missing lifetime specifier [E0106]"*/&/*error**/i32 { unimplemented!() }

        struct Foo {}
        impl Foo {
            fn method1(&self, a: &i32, b: &i32) -> &i32 { &0 }
            fn method2(self: &Self, a: &i32, b: &i32) -> &i32 { &0 }
            fn method3(self: Box<&Self>, a: &i32, b: &i32) -> &i32 { &0 }

            fn method4(self, a: &i32, b: &i32) -> /*error descr="Missing lifetime specifier [E0106]"*/&/*error**/i32 { &0 }
        }
    """)

    fun `test E0107 wrong number of lifetime parameters`() = checkByText("""
        struct Foo0;
        struct Foo1<'a>(&'a str);
        struct Foo2<'a, 'b> { a: &'a u32, b: &'b str }

        struct Ok<'a> { a: Foo0, b: Foo1<'a>, c: Foo2<'a, 'a> }

        struct Err<'a, 'b, 'c> {
            a: <error descr="Wrong number of lifetime arguments: expected 0, found 2 [E0107]">Foo0<'a, 'b></error>,
            b: <error descr="Wrong number of lifetime arguments: expected 1, found 3 [E0107]">Foo1<'a, 'b, 'c></error>,
            c: <error descr="Wrong number of lifetime arguments: expected 2, found 1 [E0107]">Foo2<'a></error>,
        }
        type TErr<'a> = <error descr="Wrong number of lifetime arguments: expected 2, found 1 [E0107]">Foo2<'a></error>;
        enum EErr<'a> { E(Box<<error descr="Wrong number of lifetime arguments: expected 1, found 2 [E0107]">Foo1<'a, 'a></error>>) }
    """)
}
