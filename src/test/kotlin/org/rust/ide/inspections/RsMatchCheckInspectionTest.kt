/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.inspections.checkMatch.RsMatchCheckInspection

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection::class) {

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

    fun `test binding useless`() = checkFixByText("Remove useless arm", """
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
                123 => {},
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

    fun `test enum useless pattern with guard`() = checkFixByText("Remove useless pattern", """
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
                123 => {},
                0x7C => {},
                0o175 => {},
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

    fun `test simple boolean exhaustive`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let a = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                true => {}
            }
        }
    """, """
        fn main() {
            let a = true;
            match a {
                true => {}
                false => {}
            }
        }
    """)

    fun `test simple int exhaustive`() = checkFixByText("Add _ pattern", """
        fn main() {
            let a = 3;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                3 => {}
                1 => {}
            }
        }
    """, """
        fn main() {
            let a = 3;
            match a {
                3 => {}
                1 => {}
                _ => {}
            }
        }
    """)

    fun `test simple double exhaustive`() = checkFixByText("Add _ pattern", """
        fn main() {
            let a = 3.9;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                3.1 => {}
                1.777 => {}
            }
        }
    """, """
        fn main() {
            let a = 3.9;
            match a {
                3.1 => {}
                1.777 => {}
                _ => {}
            }
        }
    """)

    fun `test simple string exhaustive`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let a = "str";
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                "test1" => {}
                "test2" => {}
            }
        }
    """, """
        fn main() {
            let a = "str";
            match a {
                "test1" => {}
                "test2" => {}
                &_ => {}
            }
        }
    """)

    fun `test simple char exhaustive`() = checkFixByText("Add _ pattern", """
        fn main() {
            let a = 'c';
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                'w' => {}
                'h' => {}
            }
        }
    """, """
        fn main() {
            let a = 'c';
            match a {
                'w' => {}
                'h' => {}
                _ => {}
            }
        }
    """)

    fun `test simple path exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                E::B => {}
            }
        }
    """, """
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match a {
                E::B => {}
                E::A => {}
                E::C => {}
            }
        }
    """)

    fun `test path with use exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A, B, C }
        fn main() {
            use E::*;
            let a = A;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> a {
                B => {}
            }
        }
    """, """
        enum E { A, B, C }
        fn main() {
            use E::*;
            let a = A;
            match a {
                B => {}
                A => {}
                C => {}
            }
        }
    """)

    fun `test pair of bool exhaustive`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let x = (true, true);
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {
                (false, true) => {}
            }
        }
    """, """
        fn main() {
            let x = (true, true);
            match x {
                (false, true) => {}
                (true, _) => {}
            }
        }
    """)

    fun `test pair of paths exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A, B, C }
        fn main() {
            let ab = (E::A, E::B);
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> ab {
                (E::A, E::A) => {}
            }
        }
    """, """
        enum E { A, B, C }
        fn main() {
            let ab = (E::A, E::B);
            match ab {
                (E::A, E::A) => {}
                (E::B, _) => {}
                (E::C, _) => {}
            }
        }
    """)

    fun `test nested enum exhaustive`() = checkFixByText("Add remaining patterns", """
        enum Animal { Dog(Color), Cat(Color), Horse(Color) }
        enum Color { Black, White }
        fn main() {
            let dog = Animal::Dog(Color::Black);
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> dog {
                Animal::Cat(Color::White) => {}
            }
        }
    """, """
        enum Animal { Dog(Color), Cat(Color), Horse(Color) }
        enum Color { Black, White }
        fn main() {
            let dog = Animal::Dog(Color::Black);
            match dog {
                Animal::Cat(Color::White) => {}
                Animal::Dog(_) => {}
                Animal::Horse(_) => {}
            }
        }
    """)

    fun `test enum pattern with guard exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A(i32), B(i32), C }
        fn foo(e: E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> e {
                E::A(_) | E::C => {}
                E::B(x) if x > 0 => {}
            }
        }
    """, """
        enum E { A(i32), B(i32), C }
        fn foo(e: E) {
            match e {
                E::A(_) | E::C => {}
                E::B(x) if x > 0 => {}
                E::B(_) => {}
            }
        }
    """)

    fun `test match ergonomics exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> &e {
                B => {}
            }
        }
    """, """
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            match &e {
                B => {}
                A(_) => {}
            }
        }
    """)

    fun `test match reference exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> e {
                &B => {}
            }
        }
    """, """
        enum E { A(i32), B }
        use E::*;
        fn foo(e: &E) {
            match e {
                &B => {}
                &A(_) => {}
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

    // https://github.com/intellij-rust/intellij-rust/issues/3776
    fun `test tuple with multiple types exhaustiveness`() = checkByText("""
        enum E { A }

        fn main() {
            match (E::A, true) {
                (E::A, true) => {}
                (E::A, false) => {}
            }
        }
        """)

    fun `test struct with multiple types exhaustiveness`() = checkByText("""
        enum E { A }
        struct S { e: E, x: bool }

        fn main() {
            match (S { e: E::A, x: true }) {
                S { e: E::A, x: true } => {}
                S { e: E::A, x: false } => {}
            }
        }
        """)

    fun `test tuple with different types remaining`() = checkFixByText("Add remaining patterns", """
        enum E { A, B }

        fn main() {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> (E::A, true) {
                (E::A, true) => {}
                (E::A, false) => {}
                (E::B, true) => {}
            }
        }
        """, """
        enum E { A, B }

        fn main() {
            match (E::A, true) {
                (E::A, true) => {}
                (E::A, false) => {}
                (E::B, true) => {}
                (E::B, false) => {}
            }
        }
        """)

    fun `test struct with unknown type parameter`() = checkByText("""
        struct S<T> { x: T }

        fn foo(s: S<MyBool>) {
            match s {
                S { x: true } => {}
            }
        }
    """)

    fun `test enum with type parameters`() = checkByText("""
        enum E { A(F<i32>) }
        enum F<T> { B(T), C }

        fn foo(x: E) {
            match x {
                E::A(F::B(b)) => {}
                E::A(F::C) => {}
            }
        }
    """)

    fun `test enum with type parameters exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E<T> { A(T), B }

        fn bar(e: E<bool>) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> e {
                E::A(true) => {},
                E::B => {},
            }
        }
    """, """
        enum E<T> { A(T), B }

        fn bar(e: E<bool>) {
            match e {
                E::A(true) => {},
                E::B => {},
                E::A(false) => {}
            }
        }
    """)

    fun `test struct with type parameters exhaustive`() = checkFixByText("Add remaining patterns", """
        enum E<T> { A(S<T>), B }
        struct S<T> { x: T}

        fn bar(e: E<bool>) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> e {
                E::A(S { x: true }) => {},
                E::B => {},
            }
        }
    """, """
        enum E<T> { A(S<T>), B }
        struct S<T> { x: T}

        fn bar(e: E<bool>) {
            match e {
                E::A(S { x: true }) => {},
                E::B => {},
                E::A(S { x: false }) => {}
            }
        }
    """)

    fun `test full and shorthand pat fields`() = checkByText("""
        struct S { x: bool, y: i32 }
        
        fn foo(s: S) {
            match s {
                S { x: true, y } => {}
                S { x: false, y: _ } => {}
            }
        }
    """)

    fun `test ignored fields 1`() = checkByText("""
        struct S { x: bool, y: i32 }
        
        fn foo(s: S) {
            match s {
                S { x: true, y } => {}
                S { x: false, .. } => {}
            }
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/3958
    fun `test ignored fields 2`() = checkByText("""
        struct S { s: String, e: E }
        enum E { A, B }
        
        fn foo(s: S) {
            match s {
                S { e: E::A, s } => {}
                S { e: E::B, .. } => {}
            }
        }
    """)

    fun `test ignored fields 3`() = checkFixByText("Add remaining patterns", """
        struct S { a: bool, b: bool, c: bool }
        
        fn foo(s: S) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> s {
                S { a: true, .. } => {}
                S { b: true, .. } => {}
            }
        }
    """, """
        struct S { a: bool, b: bool, c: bool }
        
        fn foo(s: S) {
            match s {
                S { a: true, .. } => {}
                S { b: true, .. } => {}
                S { a: false, b: false, c: _ } => {}
            }
        }
    """)

    fun `test import unresolved type`() = checkFixByText("Add remaining patterns", """
        use a::foo;
        use a::E::A;

        mod a {
            pub enum E { A, B }
            pub fn foo() -> E { E::A }
        }

        fn main() {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> foo() {
                A => {}
            };
        }
    """, """
        use a::{foo, E};
        use a::E::A;

        mod a {
            pub enum E { A, B }
            pub fn foo() -> E { E::A }
        }

        fn main() {
            match foo() {
                A => {}
                E::B => {}
            };
        }
    """)
}
