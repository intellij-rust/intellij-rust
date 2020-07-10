/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.match

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.checkMatch.RsUnreachablePatternsInspection

class RsUnreachablePatternsInspectionTest : RsInspectionsTestBase(RsUnreachablePatternsInspection::class) {

    fun `test simple boolean useless`() = checkByText("""
        fn main() {
            let a = true;
            match a {
                true => {}
                false => {}
                <warning descr="Unreachable pattern">true</warning> => {}
                <warning descr="Unreachable pattern">true</warning> => {}
                <warning descr="Unreachable pattern">false</warning> => {}
            }
        }
    """)

    fun `test binding useless`() = checkFixByText("Remove unreachable match arm", """
        fn main() {
            let x = 42;
            match x {
                y => {}
                <warning descr="Unreachable pattern">z/*caret*/</warning> => {}
            };
        }
    """, """
        fn main() {
            let x = 42;
            match x {
                y => {}
            };
        }
    """)

    fun `test integer literals useless`() = checkByText("""
        fn main() {
            let a = 2;
            match a {
                123 => {}
                <warning descr="Unreachable pattern">0x7B</warning> => {},
                <warning descr="Unreachable pattern">0o173</warning> => {},
                <warning descr="Unreachable pattern">0b01_11_10_11</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test simple double useless`() = checkByText("""
        fn main() {
            let a = 9.9;
            match a {
                1.0 => {}
                2.3 => {}
                3.6 => {}
                <warning descr="Unreachable pattern">2.3</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test simple string useless`() = checkByText("""
        fn main() {
            let a = "str";
            match a {
                "test" => {}
                "test2" => {}
                <warning descr="Unreachable pattern">"test"</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test simple char useless`() = checkByText("""
        fn main() {
            let a = 'h';
            match a {
                'c' => {}
                'd' => {}
                <warning descr="Unreachable pattern">'c'</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test simple range useless`() = checkByText("""
        fn main() {
            let a = 2;
            match a {
                0...10 => {}
                <warning descr="Unreachable pattern">3...8</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test simple path enum useless`() = checkByText("""
        enum E { A, B }
        fn main() {
            let a = E::A;
            match a {
                E::A => {}
                E::B => {}
                <warning descr="Unreachable pattern">E::A</warning> => {}
                <warning descr="Unreachable pattern">E::B</warning> => {}
            }
        }
    """)

    fun `test simple path struct wfUseless`() = checkByText("""
        struct S;
        fn main() {
            let a = S;
            match a {
                S => {}
                <warning descr="Unreachable pattern">S</warning> => {}
            }
        }
    """)

    fun `test enum with boolean useless`() = checkByText("""
        enum E { A(bool) }
        fn main() {
            let a = E::A(true);
            match a {
                E::A(true) => {}
                E::A(false) => {}
                <warning descr="Unreachable pattern">E::A(true)</warning> => {}
            }
        }
    """)

    fun `test enum with int useless`() = checkByText("""
        enum E { A(i32) }
        fn main() {
            let a = E::A(2);
            match a {
                E::A(3) => {}
                E::A(5) => {}
                <warning descr="Unreachable pattern">E::A(3)</warning> => {}
                E::A(x) => {}
            }
        }
    """)

    fun `test enum with double useless`() = checkByText("""
        enum E { A(f64) }
        fn main() {
            let a = E::A(2.3);
            match a {
                E::A(3.6) => {}
                E::A(5.8) => {}
                <warning descr="Unreachable pattern">E::A(3.6)</warning> => {}
                E::A(x) => {}
            }
        }
    """)

    fun `test enum with string useless`() = checkByText("""
        enum E { A(str) }
        fn main() {
            let a = E::A("str");
            match a {
                E::A("test") => {}
                E::A("test2") => {}
                <warning descr="Unreachable pattern">E::A("test")</warning> => {}
                E::A(x) => {}
            }
        }
    """)

    fun `test enum with char useless`() = checkByText("""
        enum E { A(char) }
        fn main() {
            let a = E::A('c');
            match a {
                E::A('d') => {}
                E::A('b') => {}
                <warning descr="Unreachable pattern">E::A('b')</warning> => {}
                E::A(x) => {}
            }
        }
    """)

    fun `test enum with path enum useless`() = checkByText("""
        enum E { A, B }
        enum F { A(E) }
        fn main() {
            let a = F::A(E::A);
            match a {
                F::A(E::A) => {}
                F::A(E::B) => {}
                <warning descr="Unreachable pattern">F::A(E::A)</warning> => {}
            }
        }
    """)

    fun `test enum with path struct wf useless`() = checkByText("""
        struct S;
        enum E { A(S) }
        fn main() {
            let a = E::A(S);
            match a {
                E::A(S) => {}
                <warning descr="Unreachable pattern">E::A(S)</warning> => {}
            }
        }
    """)

    fun `test enum with enum useless`() = checkByText("""
        enum E { A(i32), B }
        enum F { A(str), B(E) }
        enum G { A(F) }
        fn main() {
            let a = G::A(F::B(E::A(2)));
            match a {
                G::A(F::B(E::A(3))) => {}
                G::A(F::B(E::A(x))) => {}
                G::A(F::B(E::B)) => {}
                G::A(F::A("str")) => {}
                <warning descr="Unreachable pattern">G::A(F::B(E::A(5)))</warning> => {}
                <warning descr="Unreachable pattern">G::A(F::B(E::A(_)))</warning> => {}
                <warning descr="Unreachable pattern">G::A(F::B(E::B))</warning> => {}
                <warning descr="Unreachable pattern">G::A(F::B(_))</warning> => {}
                <warning descr="Unreachable pattern">G::A(F::A("str"))</warning> => {}
                G::A(F::A(_)) => {}
                <warning descr="Unreachable pattern">G::A(F::A("str2"))</warning> => {}
            }
        }
    """)

    fun `test enum useless pattern with guard`() = checkFixByText("Remove unreachable pattern", """
        enum E { A(i32), B(i32) }
        fn foo(e: E) {
            match e {
                E::A(_) => {}
                E::B(x) | <warning descr="Unreachable pattern">E::A(x)/*caret*/</warning> if x > 0 => {}
                E::B(_) => {}
            }
        }
    """, """
        enum E { A(i32), B(i32) }
        fn foo(e: E) {
            match e {
                E::A(_) => {}
                E::B(x) if x > 0 => {}
                E::B(_) => {}
            }
        }
    """)

    fun `test struct with boolean useless`() = checkByText("""
        struct S { a: bool }
        fn main() {
            let a = S { a: true };
            match a {
                S { a: true } => {}
                S { a: false } => {}
                <warning descr="Unreachable pattern">S { a: true }</warning> => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}
                <warning descr="Unreachable pattern">S { a }</warning> => {}
            }
        }
    """)

    fun `test struct with int useless`() = checkByText("""
        struct S { a: i32 }
        fn main() {
            let a = S { a: 1 };
            match a {
                S { a: 3 } => {}
                S { a: 4 } => {}
                <warning descr="Unreachable pattern">S { a: 3 }</warning> => {}
                S { a } => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}
            }
        }
    """)

    fun `test struct with double useless`() = checkByText("""
        struct S { a: f64 }
        fn main() {
            let a = S { a: 1.6 };
            match a {
                S { a: 3.4 } => {}
                S { a: 4.1 } => {}
                <warning descr="Unreachable pattern">S { a: 3.4 }</warning> => {}
                S { a } => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}
            }
        }
    """)

    fun `test struct with string useless`() = checkByText("""
        struct S { a: str }
        fn main() {
            let a = S { a: "str" };
            match a {
                S { a: "test" } => {}
                S { a: "test2" } => {}
                <warning descr="Unreachable pattern">S { a: "test" }</warning> => {}
                S { a } => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}
            }
        }
    """)

    fun `test struct with char useless`() = checkByText("""
        struct S { a: char }
        fn main() {
            let a = S { a: 'c' };
            match a {
                S { a: 'w' } => {}
                S { a: 'c' } => {}
                <warning descr="Unreachable pattern">S { a: 'w' }</warning> => {}
                S { a } => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}
            }
        }
    """)

    fun `test struct with path enum useless`() = checkByText("""
        enum E { A, B }
        struct S { a: E }
        fn main() {
            let a = S { a: E::A };
            match a {
                S { a: E::A } => {}
                S { a: E::B } => {}
                <warning descr="Unreachable pattern">S { a: E::A }</warning> => {}
                <warning descr="Unreachable pattern">S { a }</warning> => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}

            }
        }
    """)

    fun `test struct with path struct wfUseless`() = checkByText("""
        struct E;
        struct S { a: E }
        fn main() {
            let a = S { a: E };
            match a {
                S { a: E } => {}
                <warning descr="Unreachable pattern">S { a: E }</warning> => {}
                <warning descr="Unreachable pattern">S { a }</warning> => {}
                <warning descr="Unreachable pattern">S { a: x }</warning> => {}

            }
        }
    """)

    fun `test struct with struct useless`() = checkByText("""
        struct E { a: i32, b: char }
        struct S { a: E }
        fn main() {
            let a = S { a: E {a: 2, b: 'c'} };
            match a {
                S { a: E { a: 2, b: 'w' } } => {}
                <warning descr="Unreachable pattern">S { a: E { b: 'w', a: 2 } }</warning> => {}
                S { a: E { a: _, b: 'w' } } => {}
                <warning descr="Unreachable pattern">S { a: E { b: 'w', a: 999 } }</warning> => {}
                S { a: E { a: _, b: _ } } => {}
                <warning descr="Unreachable pattern">_</warning> => {}
            }
        }
    """)

    fun `test ref pattern useful`() = checkByText("""
        struct S { x: i32 }
        enum E { A, B(S) }

        fn foo(e: E) {
            match e {
                E::A => {}
                E::B(ref s) if s.x > 0 => {}
                E::B(s) => {}
            }
        }
    """)

    fun `test first ref pattern useful`() = checkByText("""
        struct S { x: i32 }
        enum E { A, B(S) }

        fn foo(e: E) {
            match e {
                E::B(ref s) if s.x > 0 => {}
                E::B(s) => {}
                _ => {}
            }
        }
    """)

    fun `test integer literals useful`() = checkByText("""
        fn foo(n: i32) {
            match n {
                123 => {}
                0x7C => {}
                0o175 => {}
                0b01_11_11_10 => {}
                _ => {}
            }
        }
    """)

    fun `test unresolved`() = checkByText("""
        fn foo(s: MyEnum) {
            match s {
                MyEnum::A => {}
                MyEnum::B => {}
                _ => {}
            }
        }
    """)

    fun `test const useful`() = checkByText("""
        const ONE: i32 = 1;
        const TWO: i32 = 2;
        fn foo(n: i32) {
            match n {
                ONE => {}
                TWO => {}
                _ => {}
            }
        }
    """)

    fun `test match ergonomics useful`() = checkByText("""
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            match &e {
                A(x) => {}
                B => {}
            }
            match &e {
                B => {}
                A(x) => {}
            }
        }
    """)

    fun `test match reference useful`() = checkByText("""
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            match e {
                &A(x) => {}
                &B => {}
            }
            match &e {
                &B => {}
                &A(x) => {}
            }
        }
    """)

    fun `test const int expr evaluation`() = checkByFileTree("""
    //- main.rs
        mod foo;
        const MAX: i32 = 10;
        const MIN: i32 = -10;
        const MID: i32 = (MAX + MIN) / 2;
    //- foo.rs
        use super::{MAX, MIN, MID};
        fn foo(v: i32) {
            match v/*caret*/ {
                1 => {}
                MIN...MAX => {}
                <warning descr="Unreachable pattern">-3</warning> => {}
                <warning descr="Unreachable pattern">-5..MID</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test const bool expr evaluation`() = checkByFileTree("""
    //- main.rs
        mod foo;
        const TRUE: bool = true;
        const FALSE: bool = !TRUE;
        const VALUE: bool = FALSE && true || TRUE;
    //- foo.rs
        use super::{FALSE, VALUE};
        fn foo(v: bool) {
            match v/*caret*/ {
                true => {}
                FALSE => {}
                <warning descr="Unreachable pattern">VALUE</warning> => {}
                <warning descr="Unreachable pattern">_</warning> => {}
            }
        }
    """)

    fun `test const float expr evaluation`() = checkByFileTree("""
    //- main.rs
        mod foo;
        const PI: f32 = 3.14;
        const E: f32 = 2.718;
    //- foo.rs
        use super::{PI, E};
        fn foo(v: f32) {
            match v/*caret*/ {
                PI => {}
                E => {}
                <warning descr="Unreachable pattern">3.14</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test const char expr evaluation`() = checkByFileTree("""
    //- main.rs
        mod foo;
        const A: char = 'A';
        const Z: char = 'Z';
        const F: char = 'f';
    //- foo.rs
        use super::{A, F, Z};
        fn foo(v: char) {
            match v/*caret*/ {
                A...'Z' => {}
                F => {}
                'a' => {}
                <warning descr="Unreachable pattern">Z</warning> => {}
                <warning descr="Unreachable pattern">'f'</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test const str expr evaluation`() = checkByFileTree("""
    //- main.rs
        mod foo;
        const HELLO: &str = "hello!";
        const FOO: &'static str = "FOO";
    //- foo.rs
        use super::{HELLO, FOO};
        fn foo(v: &str) {
            match v/*caret*/ {
                "hello" => {}
                "hello!" => {}
                <warning descr="Unreachable pattern">HELLO</warning> => {}
                FOO => {}
                <warning descr="Unreachable pattern">"FOO"</warning> => {}
                _ => {}
            }
        }
    """)

    fun `test unknown value`() = checkByText("""
        fn main() {
            match 42 {
                0..UNRESOLVED => {}
                20..50 => {}
                _ => {}
            }
        }
    """)

    fun `test deny unreachable_patterns useless`() = checkByText("""
        #[deny(unreachable_patterns)]
        fn foo(a: bool) {
            match a {
                true => {}
                false => {}
                <error descr="Unreachable pattern">true</error> => {}
            }
        }
    """)

    fun `test allow unreachable_patterns useless`() = checkByText("""
        #[allow(unreachable_patterns)]
        fn foo(a: bool) {
            match a {
                true => {}
                false => {}
                true => {}
            }
        }
    """)
}
