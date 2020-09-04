/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinLiteralFieldListIntentionTest : RsIntentionTestBase(JoinLiteralFieldListIntention::class) {
    fun `test one parameter`() = doAvailableTest("""
        struct S { x: i32 }
        fn foo() {
            S {
                /*caret*/x: 0
            };
        }
    """, """
        struct S { x: i32 }
        fn foo() {
            S { x: 0 };
        }""")

    fun `test two parameters`() = doAvailableTest("""
        struct S { x: i32, y: i32 }
        fn foo() {
            S {
                /*caret*/x: 0,
                y: 0
            };
        }
    """, """
        struct S { x: i32, y: i32 }
        fn foo() {
            S { x: 0, y: 0 };
        }
    """)

    fun `test no line breaks`() = doUnavailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { /*caret*/x: 0, y: 0, z: 0 };
        }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { x: 0, /*caret*/y: 0,
                z: 0 };
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { x: 0, y: 0, z: 0 };
        }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: 0, y: 0, z: 0/*caret*/
            };
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { x: 0, y: 0, z: 0 };
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { /*caret*/x: 0, /* comment */ y: 0, z: 0 };
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { /*caret*/x: 0, /*
                comment
                */ y: 0,
                z: 0
            };
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { x: 0, /*
                comment
                */ y: 0, z: 0 };
        }
    """)

    fun `test init shorthand`() = doAvailableTest("""
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            let x = 1;
            let y = 2;
            S {
                /*caret*/x,
                z: 3,
                y
            };
        }
   """, """
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            let x = 1;
            let y = 2;
            S { x, z: 3, y };
        }
    """)

    fun `test dotdot`() = doAvailableTest("""
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            let s = S { x: 0, y: 1, z: 2 };
            S {
                /*caret*/x: 0,
                ..s
            };
        }
    """, """
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            let s = S { x: 0, y: 1, z: 2 };
            S { x: 0, ..s };
        }
    """)

    fun `test enum`() = doAvailableTest("""
        enum E { A { x: i32, y: i32, z: i32 } }
        fn foo() {
            let e = E::A {
                /*caret*/ x: 0,
                y: 0,
                z: 0
            };
        }
   """, """
        enum E { A { x: i32, y: i32, z: i32 } }
        fn foo() {
            let e = E::A { x: 0, y: 0, z: 0 };
        }
    """)

    fun `test has end-of-line comments`() = doUnavailableTest("""
        struct S { x: i32, y: i32 }
        fn foo() {
            S {
                /*caret*/x: 0, // comment
                y: 0
            };
        }
    """)
}
