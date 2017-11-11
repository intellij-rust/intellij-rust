/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.intellij.lang.annotations.Language
import org.rust.ide.formatter.settings.RsCodeStyleSettings
import org.rust.lang.RsLanguage

class RsFormatterTest : RsFormatterTestBase() {
    fun testBlocks() = doTest()
    fun testItems() = doTest()
    fun testExpressions() = doTest()
    fun testArgumentAlignment() = doTest()
    fun testArgumentIndent() = doTest()
    fun testTraits() = doTest()
    fun testTupleAlignment() = doTest()
    fun testChainCallAlignmentOff() = doTest()
    fun testChainCallIndent() = doTest()

    fun testChainCallAlignment() {
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

    fun testAlignParamsOff() {
        common().ALIGN_MULTILINE_PARAMETERS = false
        doTest()
    }

    fun testAlignParamsInCallsOff() {
        common().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        doTest()
    }

    fun testAlignRetOff() {
        custom().ALIGN_RET_TYPE = false
        doTest()
    }

    fun testAlignWhereOn() {
        custom().ALIGN_WHERE_CLAUSE = true
        doTest()
    }

    fun testAlignWhereBoundsOff() {
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

    fun testAlignTypeParamsOn() {
        custom().ALIGN_TYPE_PARAMS = true
        doTest()
    }

    fun testMinNumberOfBlankLines() {
        custom().MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 2
        doTest()
    }

    fun testAlignReturnType() = doTest()

    fun testAllowOneLineMatchOff() = doTest()
    fun testAllowOneLineMatch() {
        custom().ALLOW_ONE_LINE_MATCH = true
        doTest()
    }

    fun testMacroUse() = doTest()
    fun testAttributes() = doTest()

    // FIXME: this test is way too big
    fun testSpacing() = doTest()

    fun testIssue451() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/451
    fun testIssue526() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/526
    fun testIssue569() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/569

    // https://github.com/intellij-rust/intellij-rust/issues/543
    fun testIssue543a() = checkNotChanged("""
        pub type TeraResult<T> = Result<T, TeraError>;
    """)

    fun testIssue543b() = checkNotChanged("""
        impl_rdp! {
            grammar! {
                expression = _{ paren ~ expression? }
                paren      =  { ["("] ~ expression? ~ [")"] }
            }
        }
    """)

    fun testIssue543c() = checkNotChanged("""
        fn main() {
            if previous_end < token.start {
                space_tokens.push((
                    insert_at,
                    Token::new(Rule::text, previous_end, token.start)
                ));
            }
        }
    """)

    fun testElse() = doTest()

    fun testIssue654() = doTest()

    fun testUseRoot() = doTest() // https://github.com/intellij-rust/intellij-rust/issues/746

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

    fun testImportGlobAlignment() = doTextTest("""
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

    private fun common() = getSettings(RsLanguage)
    private fun custom() = settings.getCustomSettings(RsCodeStyleSettings::class.java)

}
