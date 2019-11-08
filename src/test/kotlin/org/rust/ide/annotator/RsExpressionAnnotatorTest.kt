/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsExpressionAnnotatorTest : RsAnnotatorTestBase(RsExpressionAnnotator::class) {

    fun `test unnecessary parens`() = checkWarnings("""

        struct S { f: i32 }

        fn test() {
            if <weak_warning descr="Predicate expression has unnecessary parentheses">(true)</weak_warning> {
                let _ = 1;
            }

            for x in <weak_warning descr="For loop expression has unnecessary parentheses">(0..10)</weak_warning> {
                let _ = 1;
            }

            match <weak_warning descr="Match expression has unnecessary parentheses">(x)</weak_warning> {
                _ => println!("Hello world")
            }

            if <weak_warning descr="Predicate expression has unnecessary parentheses">(pred)</weak_warning> {
                return <weak_warning descr="Return expression has unnecessary parentheses">(true)</weak_warning>;
            }

            while <weak_warning descr="Predicate expression has unnecessary parentheses">(true)</weak_warning> {
                let _ = 1;
            }

            let e = (<weak_warning descr="Redundant parentheses in expression">(4 + 3)</weak_warning>);
            let _ = match <weak_warning descr="Match expression has unnecessary parentheses">(1)</weak_warning> { _ => 0 };
            let _ = match (S { f: 0 }) { _ => 0 };

            if (S { f: 0 } == S { f: 1 }) {
                let _ = 1;
            }
        }
        """)

    fun `test nested parentheses`() = checkWarnings("""
        fn test() {
            let _ = (<weak_warning descr="Redundant parentheses in expression">((((4 + 3))))</weak_warning>);
        }
        """)

    fun `test struct expr`() = checkWarnings("""

        #[derive(Default)]
        struct S {
            foo: i32,
            bar: i32
        }

        #[derive(Default)]
        struct T(i32, i32);

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

            let _ = T {
                0: 92,
                1: 11,
                <error descr="No such field">2</error>: 62,
            };

            let _ = S {
                foo: 92,
                ..S::default()
            };

            let _ = T {
                0: 92,
                ..T::default()
            };

            let foo = 42;
            let bar = 24;

            let _ = S {
                foo,
                bar
            };

            let _ = <error descr="Some fields are missing">S</error> {
                foo: 92,
            };

            let _ = <error descr="Some fields are missing">T</error> {
                0: 92,
            };

            let _ = <error descr="Some fields are missing">S</error> {
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
            };

            let _ = <error descr="Some fields are missing">T</error> {
                0: 1,
                <error descr="Duplicate field">0</error>: 2,
            };

            let _ = S {
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
                ..S::default()
            };

            let _ = T {
                0: 1,
                <error descr="Duplicate field">0</error>: 2,
                ..T::default()
            };

            let _ = Empty { };

            let _ = <error descr="Some fields are missing">E::V</error> {
                <error descr="No such field">bar</error>: 92
            };
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

    fun `test union`() = checkWarnings("""
        union U { a: i32, b: f32 }

        fn main() {
            let _ = U { a: 92 };
            let _ = U <error descr="Union expressions should have exactly one field">{ a: 92, b: 92.0}</error>;
        }
    """)
}
