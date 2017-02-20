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

            let _ = S <error descr="Some fields are missing">{
                foo: 92,
            }</error>;

            let _ = S <error descr="Some fields are missing">{
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
            }</error>;

            let _ = S {
                foo: 1,
                <error>foo</error>: 2,
                ..S::default()
            };

            let _ = Empty { };

            let _ = E::V <error>{
                <error>bar</error>: 92
            }</error>;
        }

        struct Win {
            foo: i32,
            #[cfg(windows)] bar: i32,
        }

        #[cfg(unix)]
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

    fun testE0384_ReassignImmutableFromBinding() = checkErrors("""
        fn main() {
            let x = 5;
            <error descr="Reassigning an immutable variable [E0384]">x = 3</error>;
        }
    """)

    fun testE0384_ReassignMutableFromBinding() = checkErrors("""
        fn main() {
            let mut x = 5;
            x = 3;
        }
    """)

    fun testE0384_ReassignMutableFromBindingWithoutAssignement() = checkErrors("""
        fn main() {
            let mut x;
            x = 3;
        }
    """)

    fun testE0384_ReassignImmutableFromBindingWithoutAssignement() = checkErrors("""
        fn main() {
            let x;
            x = 3;
        }
    """)

    fun testE0384_ReassignMutableFromBindingThroughAsterisk() = checkErrors("""
        fn main() {
            let mut x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun testE0384_ReassignImmutableFromBindingThroughAsterisk() = checkErrors("""
        fn main() {
            let x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun `test E0384 in pattern`() = checkErrors("""
        fn main() {
            let (x, mut y) = (92, 62);
            <error>x = 42</error>;
            y = 42;
        }
    """)
}
