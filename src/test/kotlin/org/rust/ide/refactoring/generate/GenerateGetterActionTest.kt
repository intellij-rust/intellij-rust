/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import java.lang.reflect.Member

class GenerateGetterActionTest : RsGenerateBaseTest() {
    override val generateId: String = "Rust.GenerateGetter"

    fun `test not available on impl trait block`() = doUnavailableTest("""
        trait T {}

        struct S {
            a: i32,
            b: i32,
        }

        impl T for S/*caret*/ {}
    """)

    fun `test not available inside method body`() = doUnavailableTest("""
        struct S {
            a: i32,
            b: i32,
        }

        impl S{
            pub fn foo(&self) {
                /*caret*/
            }
        }
    """)

    fun `test not available on tuple struct`() = doUnavailableTest("""
        struct S(u32, u32);

        impl S/*caret*/ {}
    """)

    fun `test not available on empty struct`() = doUnavailableTest("""
        struct S;

        impl S/*caret*/ {}
    """)

    fun `test not available on struct without fields`() = doUnavailableTest("""
        struct S {}

        impl S/*caret*/ {}
    """)

    fun `test primitive fields`() = doTest("""
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            /*caret*/
        }
    """, listOf(
        MemberSelection("a: i32"),
        MemberSelection("b: bool")
    ), """
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            pub fn a(&self) -> i32 {
                self.a
            }
            pub fn b(&self) -> bool {
                self.b
            }
        }
    """)

    fun `test select field`() = doTest("""
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            /*caret*/
        }
    """, listOf(
        MemberSelection("a: i32"),
        MemberSelection("b: bool", false)
    ), """
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            pub fn a(&self) -> i32 {
                self.a
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test copy impl`() = doTest("""
        #[derive(Copy, Clone)]
        struct Copyable {
            x: u64
        }

        struct S {
            a: Copyable
        }

        impl S {
            /*caret*/
        }
    """, listOf(MemberSelection("a: Copyable")), """
        #[derive(Copy, Clone)]
        struct Copyable {
            x: u64
        }

        struct S {
            a: Copyable
        }

        impl S {
            pub fn a(&self) -> Copyable {
                self.a
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test string`() = doTest("""
        struct S {
            a: String
        }

        impl S {
            /*caret*/
        }
    """, listOf(MemberSelection("a: String")), """
        struct S {
            a: String
        }

        impl S {
            pub fn a(&self) -> &str {
                &self.a
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test enum and struct fields`() = doTest("""
        enum E {
            E1, E2
        }

        struct S1 {
            a: i32,
        }

        struct S2 {
            e: E,
            s1: S1
        }

        impl S2 {
            /*caret*/
        }
    """, listOf(MemberSelection("e: E"), MemberSelection("s1: S1")), """
        enum E {
            E1, E2
        }

        struct S1 {
            a: i32,
        }

        struct S2 {
            e: E,
            s1: S1
        }

        impl S2 {
            pub fn e(&self) -> &E {
                &self.e
            }
            pub fn s1(&self) -> &S1 {
                &self.s1
            }
        }
    """)

    fun `test reference`() = doTest("""
        struct S<'a> {
            a: &'a str
        }

        impl<'a> S<'a>/*caret*/ {}
    """, listOf(MemberSelection("a: &'a str")), """
        struct S<'a> {
            a: &'a str
        }

        impl<'a> S<'a> {
            pub fn a(&self) -> &'a str {
                self.a
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test tuple copy`() = doTest("""
        struct S {
            a: (u32, u32)
        }

        impl S/*caret*/ {}
    """, listOf(MemberSelection("a: (u32, u32)")), """
        struct S {
            a: (u32, u32)
        }

        impl S {
            pub fn a(&self) -> (u32, u32) {
                self.a
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test tuple move`() = doTest("""
        struct NoCopy;
        struct S {
            a: (u32, NoCopy)
        }

        impl S {/*caret*/}
    """, listOf(MemberSelection("a: (u32, NoCopy)")), """
        struct NoCopy;
        struct S {
            a: (u32, NoCopy)
        }

        impl S {
            pub fn a(&self) -> &(u32, NoCopy) {
                &self.a
            }
        }
    """)

    fun `test generic field changed type parameter name`() = doTest("""
        struct S<T> {
            a: T
        }

        impl<R> S<R>/*caret*/ {}
    """, listOf(MemberSelection("a: R")), """
        struct S<T> {
            a: T
        }

        impl<R> S<R> {
            pub fn a(&self) -> &R {
                &self.a
            }
        }
    """)

    fun `test generic field specific type`() = doTest("""
        struct S<T> {
            a: T
        }

        impl S<u32>/*caret*/ {}
    """, listOf(MemberSelection("a: u32")), """
        struct S<T> {
            a: T
        }

        impl S<u32> {
            pub fn a(&self) -> u32 {
                self.a
            }
        }
    """)

    fun `test filter fields with a getter`() = doTest("""
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }
            /*caret*/
        }
    """, listOf(MemberSelection("b: i32")), """
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }

            pub fn b(&self) -> i32 {
                self.b
            }
        }
    """)

    fun `test unavailable when all fields have a getter`() = doUnavailableTest("""
        struct S {
            a: i32,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }
            /*caret*/
        }
    """)

    fun `test unrelated method exists`() = doTest("""
        struct S {
            a: i32,
        }

        impl S {
            fn foo() -> i32 {
                42
            }
            /*caret*/
        }
    """, listOf(MemberSelection("a: i32")), """
        struct S {
            a: i32,
        }

        impl S {
            fn foo() -> i32 {
                42
            }

            pub fn a(&self) -> i32 {
                self.a
            }
        }
    """)

    fun `test skip pub field`() = doTest("""
        struct S {
            a: i32,
            pub b: i32,
            pub(crate) c: i32,
        }

        impl S/*caret*/ {}
    """, listOf(MemberSelection("a: i32"), MemberSelection("c: i32")), """
        struct S {
            a: i32,
            pub b: i32,
            pub(crate) c: i32,
        }

        impl S {
            pub fn a(&self) -> i32 {
                self.a
            }
            pub fn c(&self) -> i32 {
                self.c
            }
        }
    """)

    fun `test type alias`() = doTest("""
        struct T;
        type Alias = T;
        struct S {
            a: Alias
        }

        impl S/*caret*/ {}
    """, listOf(MemberSelection("a: Alias")), """
        struct T;
        type Alias = T;
        struct S {
            a: Alias
        }

        impl S {
            pub fn a(&self) -> &Alias {
                &self.a
            }
        }
    """)

    fun `test move to first generated getter`() = doTest("""
        struct S {
            a: i32,
            b: bool,/*caret*/
        }
    """, listOf(
        MemberSelection("a: i32"),
        MemberSelection("b: bool")
    ), """
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            pub fn /*caret*/a(&self) -> i32 {
                self.a
            }
            pub fn b(&self) -> bool {
                self.b
            }
        }
    """)
}
