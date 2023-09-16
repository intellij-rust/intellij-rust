/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsFunctionTypeInferenceTest : RsTypificationTestBase() {
    fun `test function unification`() = testExpr("""
        fn foo<T>(t: T) {}

        fn main() {
            let a = foo;
            let b = 0;
            a(b); //^ u8
            a(1u8);
        }
    """)

    fun `test closure unification`() = testExpr("""
        fn main() {
            let a = |x| x;
            let b = 0;
                  //^ i64
            a(1i64);
            a(b);
        }
    """)

    fun `test closure to function pointer unification`() = testExpr("""
        fn main() {
            let a = |x| {};
            let b: fn(i64) = a;
            let c = 1;
                  //^ i64
            a(c);
        }
    """)


    fun `test function def to function pointer unification`() = testExpr("""
        fn foo<T>(i: T) {}
        fn main() {
            let a = foo;
            let b: fn(i64) = a;
            let c = 1;
                  //^ i64
            a(c);
        }
    """)
}
