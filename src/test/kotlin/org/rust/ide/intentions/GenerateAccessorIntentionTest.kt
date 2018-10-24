/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class GenerateAccessorIntentionTest : RsIntentionTestBase(GenerateAccessorIntention()) {
    fun `test primitive fields`() = doAvailableTest("""
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            /*caret*/
        }
    """, """
        struct S {
            a: i32,
            b: bool,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }
            fn b(&self) -> bool {
                self.b
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test enum and struct fields`() = doAvailableTest("""
        enum E {
            E1, E2
        }

        struct S1 {
            a: i32,
        }

        struct S2 {
            e: E,
            s1: S1,
            s2: String,
        }

        impl S2 {
            /*caret*/
        }
    """, """
        enum E {
            E1, E2
        }

        struct S1 {
            a: i32,
        }

        struct S2 {
            e: E,
            s1: S1,
            s2: String,
        }

        impl S2 {
            fn e(&self) -> &E {
                &self.e
            }
            fn s1(&self) -> &S1 {
                &self.s1
            }
            fn s2(&self) -> &String {
                &self.s2
            }
        }
    """)

    fun `test getter exists`() = doAvailableTest("""
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
    """, """
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }

            fn b(&self) -> i32 {
                self.b
            }
        }
    """)

    fun `test unrelated method exists`() = doAvailableTest("""
        struct S {
            a: i32,
        }

        impl S {
            fn foo() -> i32 {
                42
            }
            /*caret*/
        }
    """, """
        struct S {
            a: i32,
        }

        impl S {
            fn foo() -> i32 {
                42
            }

            fn a(&self) -> i32 {
                self.a
            }
        }
    """)

    fun `test unrelated method name conflict`() = doAvailableTest("""
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn a() -> i32 {
                42
            }
            /*caret*/
        }
    """, """
        struct S {
            a: i32,
            b: i32,
        }

        impl S {
            fn a() -> i32 {
                42
            }

            fn b(&self) -> i32 {
                self.b
            }
        }
    """)

    fun `test skip pub field`() = doAvailableTest("""
        struct S {
            a: i32,
            pub b: i32,
            c: i32,
        }

        impl S {
            /*caret*/
        }
    ""","""
        struct S {
            a: i32,
            pub b: i32,
            c: i32,
        }

        impl S {
            fn a(&self) -> i32 {
                self.a
            }
            fn c(&self) -> i32 {
                self.c
            }
        }
    """)

    fun `test impl trait block`() = doAvailableTest("""
        trait T {}

        struct S {
            a: i32,
            b: i32,
        }

        impl T for S {/*caret*/}
    """, """
        trait T {}

        struct S {
            a: i32,
            b: i32,
        }

        impl T for S {}
    """)
}
