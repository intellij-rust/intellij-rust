/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class DestructureIntentionTest : RsIntentionTestBase(DestructureIntention()) {

    fun `test destructure variable`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() { let /*caret*/x = S { x: 0, y: "" }; }
    """, """
        struct S<T, U> { x: T, y: U }
        fn main() { let /*caret*/S { x, y } = S { x: 0, y: "" }; }
    """)

    fun `test destructure parameter`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn f(/*caret*/x: S<i32, &str>) {}
    """, """
        struct S<T, U> { x: T, y: U }
        fn f(/*caret*/S { x, y }: S<i32, &str>) {}
    """)

    fun `test destructure match arm`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() {
            match (S { x: 0, y: "" }) {
               /*caret*/x => ()
            }
        }
    """, """
        struct S<T, U> { x: T, y: U }
        fn main() {
            match (S { x: 0, y: "" }) {
               /*caret*/S { x, y } => ()
            }
        }
    """)

    fun `test tuple struct`() = doAvailableTest("""
        struct S(i32, i32);
        fn f(/*caret*/x: S) {}
    """, """
        struct S(i32, i32);
        fn f(S(_0, _1): S) {}
    """)

    fun `test tuple`() = doAvailableTest("""
        fn f(/*caret*/x: (i32, u128)) {}
    """, """
        fn f((_0, _1): (i32, u128)) {}
    """)

    fun `test unit struct`() = doAvailableTest("""
        struct S;
        fn f(/*caret*/x: S) {}
    """, """
        struct S;
        fn f(S {}: S) {}
    """)

    fun `test struct with no params`() = doAvailableTest("""
        struct S {}
        fn f(/*caret*/x: S) {}
    """, """
        struct S {}
        fn f(S {}: S) {}
    """)

    fun `test tuple struct with no params`() = doAvailableTest("""
        struct S();
        fn f(/*caret*/x: S) {}
    """, """
        struct S();
        fn f(S(): S) {}
    """)

    fun `test union`() = doAvailableTest("""
        union U { x: i32, y: u128 }
        fn f(/*caret*/x: U) {}
    """, """
        union U { x: i32, y: u128 }
        fn f(U { x, y }: U) {}
    """)

    fun `test unavailable for enum`() = doUnavailableTest("""
        enum S { A, B }
        fn f(/*caret*/x: S) {}
    """)

    fun `test unavailable for unknown type`() = doUnavailableTest("""
        fn f(/*caret*/x: S) {}
    """)

    fun `test unavailable if no pat ident`() = doUnavailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() { let S { /*caret*/x, y } = S { x: S { x: 1, y: 2 }, y: "" }; }
    """)

    fun `test unavailable if private fields`() = doUnavailableTest("""
        mod a {
            pub struct S { pub x: i32, y: i32 }
        }

        fn main() {
            use crate::a::S;
            let /*caret*/x = S { x: 0, y: 1 };
        }
    """)
}
