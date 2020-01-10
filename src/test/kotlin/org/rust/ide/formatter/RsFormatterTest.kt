/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.intellij.lang.annotations.Language
import org.rust.lang.RsLanguage

class RsFormatterTest : RsFormatterTestBase() {
    fun `test blocks`() = doTest()
    fun `test items`() = doTest()
    fun `test expressions`() = doTest()
    fun `test argument indent`() = doTest()
    fun `test traits`() = doTest()
    fun `test tuple alignment`() = doTest()
    fun `test chain call indent`() = doTest()

    fun `test chain call alignment`() = doTextTest(common()::ALIGN_MULTILINE_CHAINED_METHODS, """
        fn main() {
            let foo = moo.boo().goo()
             .foo().bar()
                                     .map(|x| x.foo().doo()
                                       .moo().boo()
                                                    .bar().baz())
                      .baz().moo();
        }
    """, """
        fn main() {
            let foo = moo.boo().goo()
                         .foo().bar()
                         .map(|x| x.foo().doo()
                                   .moo().boo()
                                   .bar().baz())
                         .baz().moo();
        }
    """, """
        fn main() {
            let foo = moo.boo().goo()
                .foo().bar()
                .map(|x| x.foo().doo()
                    .moo().boo()
                    .bar().baz())
                .baz().moo();
        }
    """)

    fun `test pub`() = doTextTest("""
        pub ( crate ) struct S1;

        pub ( super ) struct S2;

        pub ( in foo :: bar ) struct S3;
    """, """
        pub(crate) struct S1;

        pub(super) struct S2;

        pub(in foo::bar) struct S3;
    """)

    fun `test align incomplete chain`() = doTextTest("""
        fn main() {
            frobnicate()
                .foo()
            .
        }
    """, """
        fn main() {
            frobnicate()
                .foo()
                .
        }
    """)

    fun `test align params`() = doTextTest(common()::ALIGN_MULTILINE_PARAMETERS, """
        fn foo(x: i32,
        y: i32,
                    z: i32) {

        }

        pub fn new<S>(shape: S,
                            material_idx: usize)
              -> Primitive
                        where S: Shape + 'static {}

        fn main() {
            fooooo(1,
            2,
                           3)
        }

        extern {
            fn variadic(a: i32,
                                    b: i32,
            c: i32,
                                ...)
        -> i32;
        }
    """, """
        fn foo(x: i32,
               y: i32,
               z: i32) {}

        pub fn new<S>(shape: S,
                      material_idx: usize)
                      -> Primitive
            where S: Shape + 'static {}

        fn main() {
            fooooo(1,
                   2,
                   3)
        }

        extern {
            fn variadic(a: i32,
                        b: i32,
                        c: i32,
                        ...)
                        -> i32;
        }
    """, """
        fn foo(x: i32,
            y: i32,
            z: i32) {}

        pub fn new<S>(shape: S,
            material_idx: usize)
            -> Primitive
            where S: Shape + 'static {}

        fn main() {
            fooooo(1,
                   2,
                   3)
        }

        extern {
            fn variadic(a: i32,
                b: i32,
                c: i32,
                ...)
                -> i32;
        }
    """)

    fun `test align params in calls`() = doTextTest(common()::ALIGN_MULTILINE_PARAMETERS_IN_CALLS, """
        fn main() {
            foooo(123,
                  456,
                  789);

            write!(123,
                   456,
                   789);

            let fooooooo = (123,
                            456,
                            789);

            match 1 {
                FooSome(1234, true,
                        5678, false,
                        91011, "foobar") => 10
            }
        }
    """, afterOff = """
        fn main() {
            foooo(123,
                456,
                789);

            write!(123,
                456,
                789);

            let fooooooo = (123,
                456,
                789);

            match 1 {
                FooSome(1234, true,
                    5678, false,
                    91011, "foobar") => 10
            }
        }
    """)

    fun `test align return type`() = doTextTest(custom()::ALIGN_RET_TYPE, """
        fn foo1(x: i32, y: String)
            -> String {}

        fn foo2(x: i32,
                y: String)
           -> String {}

        fn foo3(
            x: i32,
            y: String)
                -> String {}

        fn foo4(
            x: i32,
            y: String
        )             -> String {}

        fn foo5<T>(t: T)
            -> T
                  where T: Clone {}
    """, """
        fn foo1(x: i32, y: String)
                -> String {}

        fn foo2(x: i32,
                y: String)
                -> String {}

        fn foo3(
            x: i32,
            y: String)
            -> String {}

        fn foo4(
            x: i32,
            y: String,
        ) -> String {}

        fn foo5<T>(t: T)
                   -> T
            where T: Clone {}
    """, """
        fn foo1(x: i32, y: String)
            -> String {}

        fn foo2(x: i32,
                y: String)
            -> String {}

        fn foo3(
            x: i32,
            y: String)
            -> String {}

        fn foo4(
            x: i32,
            y: String,
        ) -> String {}

        fn foo5<T>(t: T)
            -> T
            where T: Clone {}
    """)

    fun `test align where`() = doTextTest(custom()::ALIGN_WHERE_CLAUSE, """
        fn fooooooooo(a: i32,
                      b: i32,
                      c: i32)
            where T: 'static {}
    """, afterOn = """
        fn fooooooooo(a: i32,
                      b: i32,
                      c: i32)
                      where T: 'static {}
    """)

    fun `test align where bounds`() = doTextTest(custom()::ALIGN_WHERE_BOUNDS, """
        impl moo {
            pub fn with_bindings<R, F, I>(&mut self, bindings: I, f: F) -> R
                where F: FnOnce(&mut TypeContext<'a>) -> R,
                         I: IntoIterator<Item=(&'a Ident, Type)> {}
        }
    """, """
        impl moo {
            pub fn with_bindings<R, F, I>(&mut self, bindings: I, f: F) -> R
                where F: FnOnce(&mut TypeContext<'a>) -> R,
                      I: IntoIterator<Item=(&'a Ident, Type)> {}
        }
    """, """
        impl moo {
            pub fn with_bindings<R, F, I>(&mut self, bindings: I, f: F) -> R
                where F: FnOnce(&mut TypeContext<'a>) -> R,
                    I: IntoIterator<Item=(&'a Ident, Type)> {}
        }
    """)

    fun `test indent where clause`() = doTextTest(custom()::INDENT_WHERE_CLAUSE, """
        pub fn do_stuff<T>(&self, item: &T)
                    where T: ToString {}
    """, """
        pub fn do_stuff<T>(&self, item: &T)
            where T: ToString {}
    """, """
        pub fn do_stuff<T>(&self, item: &T)
        where T: ToString {}
    """)

    fun `test align type params`() = doTextTest(custom()::ALIGN_TYPE_PARAMS, """
        struct Foo<T: A + B + 'c,
            K: X + Y + 'z> {}

        struct Foo<'x: 'y + 'z,
            K: X + Y + 'z> {}

        struct Foo<T> where T: for<'a,
        'b> Fn(i32) -> () {
        a: T
        }
    """, """
        struct Foo<T: A + B + 'c,
                   K: X + Y + 'z> {}

        struct Foo<'x: 'y + 'z,
                   K: X + Y + 'z> {}

        struct Foo<T> where T: for<'a,
                                   'b> Fn(i32) -> () {
            a: T
        }
    """, """
        struct Foo<T: A + B + 'c,
            K: X + Y + 'z> {}

        struct Foo<'x: 'y + 'z,
            K: X + Y + 'z> {}

        struct Foo<T> where T: for<'a,
            'b> Fn(i32) -> () {
            a: T
        }
    """)

    fun `test min number of blank lines`() {
        custom().MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 2
        doTest()
    }

    fun `test one line match`() = doTextTest(custom()::ALLOW_ONE_LINE_MATCH, """
        fn main() {
            let x = match func() { Ok(v) => v.unwrap_or(0), Err(_) => ()};
        }
    """, """
        fn main() {
            let x = match func() { Ok(v) => v.unwrap_or(0), Err(_) => () };
        }
    """, """
        fn main() {
            let x = match func() {
                Ok(v) => v.unwrap_or(0),
                Err(_) => ()
            };
        }
    """)

    fun `test macro use`() = doTest()
    fun `test attributes`() = doTest()

    // FIXME: this test is way too big
    fun `test spacing`() = doTest()

    fun `test issue451`() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/451
    fun `test issue526`() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/526
    fun `test issue569`() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/569

    // https://github.com/intellij-rust/intellij-rust/issues/543
    fun `test issue543a`() = checkNotChanged("""
        pub type TeraResult<T> = Result<T, TeraError>;
    """)

    fun `test issue543b`() = checkNotChanged("""
        impl_rdp! {
            grammar! {
                expression = _{ paren ~ expression? }
                paren      =  { ["("] ~ expression? ~ [")"] }
            }
        }
    """)

    fun `test issue543c`() = checkNotChanged("""
        fn main() {
            if previous_end < token.start {
                space_tokens.push((
                    insert_at,
                    Token::new(Rule::text, previous_end, token.start)
                ));
            }
        }
    """)

    fun `test else`() = doTest()

    fun `test issue654`() = doTest()

    fun `test use root`() = doTest() // https://github.com/intellij-rust/intellij-rust/issues/746

    fun `test special macros`() = doTextTest("""
        fn main() {
            try !  (foo());
            format  ! ("Hello {}", 92);
            vec ! [1, 2, 3];
            let tests = vec![
            ("1.1.1.1", true),
            ("255.0.0.0", true),
            ];

            try!(
            foo()
            );

            format!(
            "Hello, World"
            );

            assert_eq!(
            "10",
            "20"
            );

            error!(
            "Hello, World"
            );

            dbg!(
            "hello!"
            );

            await!(
            "hello!"
            );

            include_str!(
            "foo/bar.txt"
            );
        }
    """, """
        fn main() {
            try!(foo());
            format!("Hello {}", 92);
            vec![1, 2, 3];
            let tests = vec![
                ("1.1.1.1", true),
                ("255.0.0.0", true),
            ];

            try!(
                foo()
            );

            format!(
                "Hello, World"
            );

            assert_eq!(
                "10",
                "20"
            );

            error!(
                "Hello, World"
            );

            dbg!(
                "hello!"
            );

            await!(
                "hello!"
            );

            include_str!(
                "foo/bar.txt"
            );
        }
    """)

    fun `test import glob alignment`() = doTextTest("""
        use piston_window::{Button,
        Transformed};
    """, """
        use piston_window::{Button,
                            Transformed};
    """)

    fun `test string literals are left intact`() = doTextTest("""
        fn main() {
        (
        r"
        x
        "
        );
            let foo =
              "Hello
                World";
        }
    """, """
        fn main() {
            (
                r"
        x
        "
            );
            let foo =
                "Hello
                World";
        }
    """)

    fun `test string literals are left intact in macro`() = doTextTest("""
        fn а() {
            println!("{}",
        r##"Some string
        which continues on this line
        and also on this one
        "##);
        }
    """, """
        fn а() {
            println!("{}",
                     r##"Some string
        which continues on this line
        and also on this one
        "##);
        }
    """)

    fun `test macro with two idents`() = doTextTest("""
        fn а() {
            foo!bar();
        }
    """, """
        fn а() {
            foo! bar();
        }
    """)

    fun `test indent initializer expression`() = doTextTest("""
        const C: () =
        ();

        fn main() {
            let _ =
            92;
        }
    """, """
        const C: () =
            ();

        fn main() {
            let _ =
                92;
        }
    """)

    fun `test associated types`() = doTextTest("""
        fn fut() -> impl Future<Item = (),Error = ()> {}
    """, """
        fn fut() -> impl Future<Item=(), Error=()> {}
    """)

    fun `test where is indented in impls`() = doTextTest("""
        impl<T> Foo<T>
        where T: bar
        {
            fn foo() {}
        }
    """, """
        impl<T> Foo<T>
            where T: bar
        {
            fn foo() {}
        }
    """)

    fun `test negative trait bound`() = doTextTest("""
        struct Foo<T:  ?  Sized> {
            a: T,
        }

        impl<T> Foo<T>
            where T: Bar +  ?  Sized {
            fn foo<U: Bar +  ?  Sized>(a: &U) {}
        }

        impl<T:  ?  Sized > Foo<T>
        {}
    """, """
        struct Foo<T: ?Sized> {
            a: T,
        }

        impl<T> Foo<T>
            where T: Bar + ?Sized {
            fn foo<U: Bar + ?Sized>(a: &U) {}
        }

        impl<T: ?Sized> Foo<T>
        {}
    """)

    fun `test spaces in reverse turbofish paths`() = doTextTest("""
        enum E<T> {
            X(< T :: BindTransport  as  IntoFuture > :: Future),
        }
    """, """
        enum E<T> {
            X(<T::BindTransport as IntoFuture>::Future),
        }
    """)

    fun `test unary minus in range patterns`() = doTextTest("""
        fn main() {
            if let - 10 .. - 1 = - 8 {}
            if let - 10 ... - 1 = - 8 {}
            if let - 10 ..= - 1 = - 8 {}
        }
    """, """
        fn main() {
            if let -10..-1 = -8 {}
            if let -10...-1 = -8 {}
            if let -10..=-1 = -8 {}
        }
    """)

    fun `test ranges`() = doTextTest("""
        fn main() {
            let r =  ..  ;
            let r =  ..  1;
            let r = 0  ..  ;
            let r = 0  ..  1;

            let r =  ...  1;
            let r = 0  ...  1;

            let r =  ..=  1;
            let r = 0  ..=  1;
        }
    """, """
        fn main() {
            let r = ..;
            let r = ..1;
            let r = 0..;
            let r = 0..1;

            let r = ...1;
            let r = 0...1;

            let r = ..=1;
            let r = 0..=1;
        }
    """)

    fun `test preserve punctuation settings`() {
        custom().PRESERVE_PUNCTUATION = true

        // don't accidentally blow commas away with ctrl+shift+l
        @Language("Text")
        val code = """
            use foo::{bar};

            fn main() {
                let _ = S { foo: 1, bar: 2, };
                match x {
                    1 => 1,
                    2 => { 2 },
                    3 => { 3 }
                    4 => loop {},
                    5 => 5,
                    6 => if true {} else {},
                    7 => 7
                }
                return
            }
        """
        checkNotChanged(code)
    }

    fun `test extern block`() = doTextTest("""
        extern    {
            fn  foo (name   : *   const    libc   ::   c_uchar   )   ;
            fn  bar (a   :  i32,         ...        )       ->      i32     ;
            fn  baz (b   :  i64,        )       ;
        }
    """, """
        extern {
            fn foo(name: *const libc::c_uchar);
            fn bar(a: i32, ...) -> i32;
            fn baz(b: i64);
        }
    """)

    fun `test keep comments on the first line`() = doTextTest("""
        fn main() {
            match x {
        // should be kept on the first column
                1 => (),
           // should be indented properly
                2 => (),
                // is already ok
                3 => (),
            }
        }
    """, """
        fn main() {
            match x {
        // should be kept on the first column
                1 => (),
                // should be indented properly
                2 => (),
                // is already ok
                3 => (),
            }
        }
    """)

    fun `test space around assoc type binding`() = doTextTest(custom()::SPACE_AROUND_ASSOC_TYPE_BINDING, """
        fn foo<T, C>(value: T) where T: Trait<Output    =   C> {}
    """, """
        fn foo<T, C>(value: T) where T: Trait<Output = C> {}
    """, """
        fn foo<T, C>(value: T) where T: Trait<Output=C> {}
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/907
    fun `test issue 907`() = checkNotChanged("""
        struct TestStruct
        {
            a: i32,
            b: i32,
        }

        impl TestStruct
        {
            fn new() -> Self
            {
                Self
                {
                    a: 0,
                    b: 0,
                }
            }
        }

        enum TestEnum
        {
            A,
            B,
        }

        fn main()
        {
            for _ in 0..42
            {
                let i = 3;
            }
            while 1 != 1
            {
                let i = 5;
            }
            loop
            {
                let i = 10;
            }
            if 6 == 6
            {
                let x = 7;
            }
            match 8
            {
                9 => Some(()),
                _ => None,
            }
        }
    """)

    private fun common() = getSettings(RsLanguage)
    private fun custom() = settings.rust
}
