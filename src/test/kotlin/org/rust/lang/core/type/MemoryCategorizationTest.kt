/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class MemoryCategorizationTest : MemoryCategorizationTestBase() {
    fun `test declared mutable`() = testExpr("""
        fn main() {
            let mut x = 42;
            x;
          //^ Declared
        }
    """)

    fun `test declared immutable`() = testExpr("""
        fn main() {
            let mut x = 42;
            x;
          //^ Immutable
        }
    """)

    fun `test array`() = testExpr("""
        fn main() {
            let mut a: [i32; 3] = [0; 3];
            a[1];
             //^ Inherited
        }
    """)
}
