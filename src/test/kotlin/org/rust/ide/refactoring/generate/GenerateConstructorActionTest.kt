/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

class GenerateConstructorActionTest : RsGenerateBaseTest() {
    override val generateId: String = "Rust.GenerateConstructor"

    fun `test generic struct`() = doTest("""
        struct S<T> {
            n: i32,/*caret*/
            m: T
        }
    """, listOf(
        MemberSelection("n: i32", true),
        MemberSelection("m: T", true)
    ), """
        struct S<T> {
            n: i32,
            m: T
        }

        impl<T> S<T> {
            pub fn new(n: i32, m: T) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test empty type declaration`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m:
        }
    """, listOf(
        MemberSelection("n: i32", true),
        MemberSelection("m: ()", true)
    ), """
        struct S {
            n: i32,
            m:
        }

        impl S {
            pub fn new(n: i32, m: ()) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test empty struct`() = doTest("""
        struct S {/*caret*/}
    """, emptyList(), """
        struct S {}

        impl S {
            pub fn new() -> Self {
                S {}
            }
        }
    """)

    fun `test tuple struct`() = doTest("""
        struct Color(i32, i32, i32)/*caret*/;
    """, listOf(
        MemberSelection("field0: i32", true),
        MemberSelection("field1: i32", true),
        MemberSelection("field2: i32", true)
    ), """
        struct Color(i32, i32, i32);

        impl Color {
            pub fn new(field0: i32, field1: i32, field2: i32) -> Self {
                Color(field0, field1, field2)
            }
        }
    """)

    fun `test select none fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        MemberSelection("n: i32", false),
        MemberSelection("m: i64", false)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new() -> Self {
                S { n: (), m: () }
            }
        }
    """)

    fun `test select all fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        MemberSelection("n: i32", true),
        MemberSelection("m: i64", true)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new(n: i32, m: i64) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test select some fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        MemberSelection("n: i32", true),
        MemberSelection("m: i64", false)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new(n: i32) -> Self {
                S { n, m: () }
            }
        }
    """)

    fun `test generate all fields on impl`() = doTest("""
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            /*caret*/
        }
    """, listOf(
        MemberSelection("n: i32", true),
        MemberSelection("m: i64", true)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new(n: i32, m: i64) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test not available when new method exists`() = doUnavailableTest("""
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new() {}
            /*caret*/
        }
    """)

    fun `test not available on trait impl`() = doUnavailableTest("""
        trait T { fn foo() }

        struct S {
            n: i32,
            m: i64,
        }

        impl T for S {
            /*caret*/
        }
    """)

    fun `test take type parameters from impl block`() = doTest("""
        struct S<T>(T);

        impl S<i32> {
            /*caret*/
        }
    """, listOf(MemberSelection("field0: i32", true)), """
        struct S<T>(T);

        impl S<i32> {
            pub fn new(field0: i32) -> Self {
                S(field0)
            }
        }
    """)

    fun `test take lifetimes from impl block`() = doTest("""
        struct S<'a, T>(&'a T);

        impl <'a> S<'a, i32> {
            /*caret*/
        }
    """, listOf(MemberSelection("field0: &'a i32", true)), """
        struct S<'a, T>(&'a T);

        impl <'a> S<'a, i32> {
            pub fn new(field0: &'a i32) -> Self {
                S(field0)
            }
        }
    """)
}
