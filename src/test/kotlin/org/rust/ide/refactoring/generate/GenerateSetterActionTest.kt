/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import java.lang.reflect.Member

class GenerateSetterActionTest : RsGenerateBaseTest() {
    override val generateId: String = "Rust.GenerateSetter"

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
            pub fn set_a(&mut self, a: i32) {
                self.a = a;
            }
            pub fn set_b(&mut self, b: bool) {
                self.b = b;
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
            pub fn set_a(&mut self, a: i32) {
                self.a = a;
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
            pub fn set_a(&mut self, a: &'a str) {
                self.a = a;
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
            pub fn set_a(&mut self, a: R) {
                self.a = a;
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
            pub fn set_a(&mut self, a: u32) {
                self.a = a;
            }
        }
    """)

    fun `test filter fields with a getter`() = doTest("""
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn set_a(&self) {}
            /*caret*/
        }
    """, listOf(MemberSelection("b: i32")), """
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn set_a(&self) {}

            pub fn set_b(&mut self, b: i32) {
                self.b = b;
            }
        }
    """)

    fun `test unavailable when all fields have a setter`() = doUnavailableTest("""
        struct S {
            a: i32,
        }

        impl S {
            fn set_a(&self) {}
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

            pub fn set_a(&mut self, a: i32) {
                self.a = a;
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
            pub fn set_a(&mut self, a: i32) {
                self.a = a;
            }
            pub fn set_c(&mut self, c: i32) {
                self.c = c;
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
            pub fn set_a(&mut self, a: Alias) {
                self.a = a;
            }
        }
    """)
}
