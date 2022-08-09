/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnreachableCodeInspectionTest : RsInspectionsTestBase(RsUnreachableCodeInspection::class) {
    fun `test straightline with return`() = checkByText("""
        fn main() {
            let x = 1;
            return;
            <warning descr="Unreachable code">let y = 2;
            let z = 3;</warning>
        }
    """)

    fun `test complex code after return`() = checkByText("""
        fn foo(flag: bool, bar: bool) {
            let x = 1;
            return;
            <warning descr="Unreachable code">let y = 2;
            if foo {
                1;
            }
            2;
            if bar {
                2;
            }</warning>
        }
    """)

    fun `test return tail expr`() = checkByText("""
        fn main() {
            return;
        }
    """)

    fun `test unreachable tail expr`() = checkByText("""
        fn foo() -> i32 {
            return 42;
            <warning descr="Unreachable code">123</warning>
        }
    """)

    fun `test code after loop`() = checkByText("""
        fn main() {
            loop {
                1;
            }
            <warning descr="Unreachable code">2;</warning>
        }
    """)

    fun `test loop with break`() = checkByText("""
        fn foo(flag: bool) {
            loop {
                1;
                if flag {
                    break;
                }
                2;
            }
            3;
        }
    """)

    fun `test complex loop with break`() = checkByText("""
        enum E { A, B }
        fn foo(e: E, flag: bool) {
            loop {
                1;
                match e {
                    E::A => {
                        2;
                        if flag {
                            break;
                        }
                    }
                    E::B => {
                        3;
                    }
                }
                4;
            }
            5;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unreachable in closure call`() = checkByText("""
        fn foo(f: fn(i32)) {}
        fn main() {
            foo(|x| {
                if x > 0 {
                    panic!();
                    <warning descr="Unreachable code">1;</warning>
                }
            });
            2;
        }
    """)

    fun `test closure call with loop`() = checkByText("""
        fn foo(f: fn(i32)) {}
        fn main() {
            foo(|x| {
                1;
                loop {
                    2;
                }
                <warning descr="Unreachable code">3;</warning>
            });
            4;
        }
    """)

    fun `test if else stmt unconditional return`() = checkByText("""
        fn main() {
            if a {
                1;
                return;
                <warning descr="Unreachable code">2;
                3;</warning>
            } else {
                4;
                return;
                <warning descr="Unreachable code">5;</warning>
            }
            <warning descr="Unreachable code">6;</warning>
        }
    """)

    fun `test if else tail expr unconditional return`() = checkByText("""
        fn foo(flag: bool) {
            if flag {
                1;
                return;
                <warning descr="Unreachable code">2;
                3;</warning>
            } else {
                4;
                return;
                <warning descr="Unreachable code">5;</warning>
            }
        }
    """)

    fun `test let declaration with unreachable init`() = checkByText("""
        fn main() {
            let x = return;
        }
    """)

    fun `test multiple return`() = checkByText("""
        fn main() {
            return;
            <warning descr="Unreachable code">1;
            return;
            2;
            return;</warning>
        }
    """)

    fun `test remove unreachable code fix`() = checkFixByText("Remove unreachable code", """
        fn main() {
            1;
            if flag1 {
                2;
                return;
                <warning descr="Unreachable code">3;/*caret*/
                if flag2 {
                    4;
                }</warning>
            }
            5;
        }
    """, """
        fn main() {
            1;
            if flag1 {
                2;
                return;
            }
            5;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test ignore conditionally disabled unreachable code`() = checkByText("""
        fn foo() -> i32 {
            return 1;
            #[cfg(not(intellij_rust))] return 2;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test annotate conditionally enabled unreachable code`() = checkByText("""
        fn foo() -> i32 {
            return 1;
            <warning descr="Unreachable code">#[cfg(intellij_rust)]
            return 2;</warning>
        }
    """)

    fun `test allow unreachable_code`() = checkByText("""
        #[allow(unreachable_code)]
        fn foo() -> i32 {
            return 1;
            return 2;
        }
    """)

    fun `test tail macro expr`() = checkByText("""
        fn main() {
            return;
            <warning descr="Unreachable code">foo!()</warning>
        }
    """)

    fun `test tail unit expr`() = checkByText("""
        fn main() {
            return;
            <warning descr="Unreachable code">()</warning>
        }
    """)

    fun `test incomplete match expr reachable`() = checkByText("""
        fn main() {
            let x = match 3 {

            };
            return;
        }
    """)

    fun `test incomplete match stmt reachable`() = checkByText("""
        enum Foo { A(u32), B }
        fn main() {
            match f() {

            };
            return;
        }
        fn f() -> Foo { loop {} }
    """)

    fun `test incomplete match with noreturn branch`() = checkByText("""
        enum Foo { A(u32), B }
        fn main() {
            match f() {
                Foo::B => loop {},
            };
            <warning descr="Unreachable code">return;</warning>
        }
        fn f() -> Foo { loop {} }
    """)

    fun `test incomplete match with noreturn discriminant 1`() = checkByText("""
        fn main() {
            <warning descr="Unreachable code">let x = match (loop {}) { };
            return;</warning>
        }
    """)

    fun `test incomplete match with noreturn discriminant 2`() = checkByText("""
        fn main() {
            <warning descr="Unreachable code">let x = match (return) { };
            return;</warning>
        }
    """)

    fun `test incomplete match with noreturn discriminant 3`() = checkByText("""
        fn main() {
            loop {
                <warning descr="Unreachable code">let x = match (break) { };
                return;</warning>
            }
        }
    """)

    fun `test incomplete match with noreturn discriminant 4`() = checkByText("""
        fn f() -> ! { }
        fn main() {
            <warning descr="Unreachable code">let x = match f() { };
            return;</warning>
        }
    """)
}
