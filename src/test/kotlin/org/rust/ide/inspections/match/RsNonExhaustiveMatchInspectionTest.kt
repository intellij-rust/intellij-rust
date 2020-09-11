/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.match

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.checkMatch.RsNonExhaustiveMatchInspection

class RsNonExhaustiveMatchInspectionTest : RsInspectionsTestBase(RsNonExhaustiveMatchInspection::class) {

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

    fun `test add _ pattern for no expression in match`() = checkFixByText("Add _ pattern", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true =><EOLError descr="<expr> expected, got '}'"></EOLError>
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true =>,
                _ => {}
            }
        }
    """)

    fun `test add _ pattern for unit expression in match`() = checkFixByText("Add _ pattern", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => ()
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => (),
                _ => {}
            }
        }
    """)

    fun `test add _ pattern for macro expression in match`() = checkFixByText("Add _ pattern", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => println!("test")
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => println!("test"),
                _ => {}
            }
        }
    """)

    fun `test add _ pattern for dot expression in match`() = checkFixByText("Add _ pattern", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => "test".as_bytes()
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => "test".as_bytes(),
                _ => {}
            }
        }
    """)

    fun `test add _ pattern for call expression in match`() = checkFixByText("Add _ pattern", """
        fn test_fun() {}

        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => test_fun()
            }
        }
    """, """
        fn test_fun() {}

        fn main() {
            let test = true;
            match test {
                true => test_fun(),
                _ => {}
            }
        }
    """)

    fun `test add _ pattern for block expression in match`() = checkFixByText("Add _ pattern", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => {}
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => {}
                _ => {}
            }
        }
    """)

    fun `test add remaining patterns for no expression in match`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true =><EOLError descr="<expr> expected, got '}'"></EOLError>
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true =>,
                false => {}
            }
        }
    """)

    fun `test add remaining patterns for unit expression in match`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => ()
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => (),
                false => {}
            }
        }
    """)

    fun `test add remaining patterns for macro expression in match`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => println!("test")
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => println!("test"),
                false => {}
            }
        }
    """)

    fun `test add remaining patterns for dot expression in match`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => "test".as_bytes()
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => "test".as_bytes(),
                false => {}
            }
        }
    """)

    fun `test add remaining patterns for call expression in match`() = checkFixByText("Add remaining patterns", """
        fn test_fun() {}

        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => test_fun()
            }
        }
    """, """
        fn test_fun() {}

        fn main() {
            let test = true;
            match test {
                true => test_fun(),
                false => {}
            }
        }
    """)

    fun `test add remaining patterns for block expression in match`() = checkFixByText("Add remaining patterns", """
        fn main() {
            let test = true;
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> test {
                true => {}
            }
        }
    """, """
        fn main() {
            let test = true;
            match test {
                true => {}
                false => {}
            }
        }
    """)

    fun `test non-exhaustive enum match in same crate`() = checkByText("""
        #[non_exhaustive]
        enum Error {
            Variant
        }

        fn main() {
            let error = Error::Variant;

            match error {
                Error::Variant => println!("Variant")
            }
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test non-exhaustive enum match in different crate`() = checkByFileTree("""
        //- dep-lib/lib.rs
        #[non_exhaustive]
        pub enum Error {
            Variant
        }

        //- main.rs
        extern crate dep_lib_target;
        use dep_lib_target::Error;

        fn main() {
            let error = Error::Variant;

            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> error {
                Error::Variant => println!("Variant")
            }
        }
    """)

    fun `test empty match simple enum variants`() = checkFixByText("Add remaining patterns", """
        enum FooBar { Foo, Bar }

        fn foo(x: FooBar) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        enum FooBar { Foo, Bar }

        fn foo(x: FooBar) {
            match/*caret*/ x {
                FooBar::Foo => {}
                FooBar::Bar => {}
            }
        }
    """)

    fun `test empty match ergonomics`() = checkFixByText("Add remaining patterns", """
        enum E { A, B }
        fn foo(x: &E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        enum E { A, B }
        fn foo(x: &E) {
            match/*caret*/ x {
                E::A => {}
                E::B => {}
            }
        }
    """)

    fun `test empty match struct enum variants`() = checkFixByText("Add remaining patterns", """
        enum FooBar {
            Foo { foo: i32 },
            Bar { bar1: bool, bar2: f64 }
        }

        fn foo(x: FooBar) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        enum FooBar {
            Foo { foo: i32 },
            Bar { bar1: bool, bar2: f64 }
        }

        fn foo(x: FooBar) {
            match/*caret*/ x {
                FooBar::Foo { .. } => {}
                FooBar::Bar { .. } => {}
            }
        }
    """)

    fun `test empty match different enum variants`() = checkFixByText("Add remaining patterns", """
        enum Foo {
            X,
            Y(i32),
            Z { foo: bool }
        }

        fn foo(x: Foo) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        enum Foo {
            X,
            Y(i32),
            Z { foo: bool }
        }

        fn foo(x: Foo) {
            match/*caret*/ x {
                Foo::X => {}
                Foo::Y(_) => {}
                Foo::Z { .. } => {}
            }
        }
    """)

    fun `test empty match don't remove comments`() = checkFixByText("Add remaining patterns", """
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {
                // test
            }
        }
    """, """
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match/*caret*/ x {
                // test
                FooBar::Foo => {}
                FooBar::Bar => {}
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test empty match Option enum`() = checkFixByText("Add remaining patterns", """
        fn foo(x: Option<i32>) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        fn foo(x: Option<i32>) {
            match/*caret*/ x {
                None => {}
                Some(_) => {}
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test empty match Result enum`() = checkFixByText("Add remaining patterns", """
        fn foo(x: Result<i32, bool>) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        fn foo(x: Result<i32, bool>) {
            match/*caret*/ x {
                Ok(_) => {}
                Err(_) => {}
            }
        }
    """)

    fun `test empty match one variant is in scope`() = checkFixByText("Add remaining patterns", """
        mod foo {
            enum E { A, B }
        }
        use foo::E;
        use foo::E::A;
        fn foo(x: &E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> x {}
        }
    """, """
        mod foo {
            enum E { A, B }
        }
        use foo::E;
        use foo::E::A;
        fn foo(x: &E) {
            match/*caret*/ x {
                A => {}
                E::B => {}
            }
        }
    """)

    fun `test empty match import unresolved type`() = checkFixByText("Add remaining patterns", """
        use a::foo;

        mod a {
            pub enum FooBar { Foo, Bar }
            pub fn foo() -> FooBar { FooBar::Foo }
        }

        fn main() {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> foo() {};
        }
    """, """
        use a::{foo, FooBar};

        mod a {
            pub enum FooBar { Foo, Bar }
            pub fn foo() -> FooBar { FooBar::Foo }
        }

        fn main() {
            match/*caret*/ foo() {
                FooBar::Foo => {}
                FooBar::Bar => {}
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test pair of Options`() = checkByText("""
        fn foo(a: Option<bool>, b: Option<bool>) {
            match (a, b) {
                (None, None) => {}
                (Some(true), _) | (_, Some(true)) => {}
                (Some(false), _) | (_, Some(false)) => {}
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no error on pair of Options match ergonomics`() = checkByText("""
        fn foo(a: Option<bool>, b: &Option<bool>) {
            match (a, b) {
                (None, None) => {}
                (Some(true), _) | (_, &Some(true)) => {}
                (Some(false), _) | (_, Some(false)) => {}
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no error on pair of Options match ergonomics different refs`() = checkByText("""
        fn foo(a: &Option<bool>) {
            match a {
                None => {}
                &Some(true) => {}
                Some(false) => {}
            }
        }
    """)

    // TODO: better support of match ergonomics
    fun `test match ergonomics different refs`() = expect<AssertionError> {
        checkFixByText("Add remaining patterns", """
        enum E { A(i32), B, C }
        use E::*;
        fn foo(e: &E) {
            <error descr="Match must be exhaustive [E0004]">match/*caret*/</error> &e {
                B => {}
                &C => {}
            }
        }
    """, """
        enum E { A(i32), B, C }
        use E::*;
        fn foo(e: &E) {
            match &e {
                B => {}
                &C => {}
                A(_) => {}
            }
        }
    """)
    }

    fun `test no error on different expr and arm types`() = checkByText("""
        enum E1 { A, B }
        enum E2 { C, D }
        fn foo(a: E1) {
            match a {
                E2::C => {}
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no error on Fn call`() = checkByText("""
        fn foo(f: &dyn Fn() -> Option<bool>) {
            match f() {
                Some(_) => {}
                None => {}
            }
        }
    """)
}
