/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.intellij.lang.annotations.Language
import org.rust.ide.formatter.settings.RsCodeStyleSettings
import org.rust.lang.RsLanguage

class RsFormatterTest : RsFormatterTestBase() {
    fun `test blocks`() = doTest()
    fun `test items`() = doTest()
    fun `test expressions`() = doTest()
    fun `test argument alignment`() = doTest()
    fun `test argument indent`() = doTest()
    fun `test traits`() = doTest()
    fun `test tuple alignment`() = doTest()
    fun `test chain call alignment off`() = doTest()
    fun `test chain call indent`() = doTest()

    fun `test chain call alignment`() {
        common().ALIGN_MULTILINE_CHAINED_METHODS = true
        doTest()
    }

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

    fun `test align params off`() {
        common().ALIGN_MULTILINE_PARAMETERS = false
        doTest()
    }

    fun `test align params in calls off`() {
        common().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        doTest()
    }

    fun `test align ret off`() {
        custom().ALIGN_RET_TYPE = false
        doTest()
    }

    fun `test align where on`() {
        custom().ALIGN_WHERE_CLAUSE = true
        doTest()
    }

    fun `test align where bounds off`() {
        custom().ALIGN_WHERE_BOUNDS = false
        doTextTest("""
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
    }

    fun `test align type params on`() {
        custom().ALIGN_TYPE_PARAMS = true
        doTest()
    }

    fun `test min number of blank lines`() {
        custom().MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 2
        doTest()
    }

    fun `test align return type`() = doTest()

    fun `test allow one line match off`() = doTest()
    fun `test allow one line match`() {
        custom().ALLOW_ONE_LINE_MATCH = true
        doTest()
    }

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
        fn main() { if let - 10 ... - 1 = - 8 {} }
    """, """
        fn main() { if let -10 ... -1 = -8 {} }
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

    private fun common() = getSettings(RsLanguage)
    private fun custom() = settings.getCustomSettings(RsCodeStyleSettings::class.java)

}
