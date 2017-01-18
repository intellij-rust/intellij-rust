package org.rust.ide.annotator

class RsExpressionAnnotatorTest : RsAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/expressions"

    fun testUnnecessaryParens() = checkWarnings("""
        fn test() {
            if <weak_warning>(true)</weak_warning> {
                let _ = 1;
            }

            for x in <weak_warning>(0..10)</weak_warning> {
                let _ = 1;
            }

            match <weak_warning>(x)</weak_warning> {
                _ => println!("Hello world")
            }

            if <weak_warning>(pred)</weak_warning> {
                return <weak_warning>(true)</weak_warning>;
            }

            while <weak_warning>(true)</weak_warning> {
                let _ = 1;
            }

            let e = (<weak_warning descr="Redundant parentheses in expression">(4 + 3)</weak_warning>);
        }
    """)


    fun testStructExpr() = checkWarnings("""
        #[derive(Default)]
        struct S {
            foo: i32,
            bar: i32
        }

        struct Empty {}

        enum E {
            V {
                foo: i32
            }
        }

        fn main() {
            let _ = S {
                foo: 92,
                <error descr="No such field">baz</error>: 62,
                bar: 11,
            };

            let _ = S {
                foo: 92,
                ..S::default()
            };

            let _ = S {
                foo: 92,
            <error descr="Some fields are missing">}</error>;

            let _ = S {
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
            <error descr="Some fields are missing">}</error>;

            let _ = S {
                foo: 1,
                <error>foo</error>: 2,
                ..S::default()
            };

            let _ = Empty { };

            let _ = E::V {
                <error>bar</error>: 92
            <error>}</error>;
        }

        struct Win {
            foo: i32,
            #[cfg(widows)] bar: i32,
        }

        #[cnf(unix)]
        fn unix_only() {
            let w = Win { foo: 92 };
        }
    """)

    fun testUnion() = checkWarnings("""
        union U { a: i32, b: f32 }

        fn main() {
            let _ = U { a: 92 };
            let _ = U <error descr="Union expressions should have exactly one field">{ a: 92, b: 92.0}</error>;
        }
    """)

    fun testStructExprQuickFix() = checkQuickFix("Add missing fields", """
        struct S {
            foo: i32,
            bar: f64
        }

        fn main() {
            let _ = S {
                /*caret*/
            };
        }
    """, """
        struct S {
            foo: i32,
            bar: f64
        }

        fn main() {
            let _ = S {
                foo: /*caret*/(),
                bar: (),
            };
        }
    """)

    fun testStructExprQuickFix2() = checkQuickFix("Add missing fields", """
        struct S {
            a: i32,
            b: i32,
            c: i32,
            d: i32
        }

        fn main() {
            let _ = S {
                a: 92,
                c: 92/*caret*/
            };
        }
    """, """
        struct S {
            a: i32,
            b: i32,
            c: i32,
            d: i32
        }

        fn main() {
            let _ = S {
                b: /*caret*/(),
                d: (),
                a: 92,
                c: 92
            };
        }
    """)
}
