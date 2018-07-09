/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class MemoryCategorizationTest : MemoryCategorizationTestBase() {
    fun `test declared immutable`() = testExpr("""
        fn main() {
            let x = 42;
            x;
          //^ Immutable
        }
    """)

    fun `test declared mutable`() = testExpr("""
        fn main() {
            let mut x = 42;
            x;
          //^ Declared
        }
    """)

    fun `test declared immutable with immutable deref`() = testExpr("""
        fn main() {
            let y = 42;
            let x = &y;
            (*x);
             //^ Immutable
        }
    """)

    fun `test declared mutable with immutable deref`() = testExpr("""
        fn main() {
            let mut y = 42;
            let x = &y;
            (*x);
             //^ Immutable
        }
    """)

    fun `test declared mutable with mutable deref`() = testExpr("""
        fn main() {
            let mut y = 42;
            let x = &mut y;
            (*x);
             //^ Declared
        }
    """)

    fun `test immutable array`() = testExpr("""
        fn main() {
            let a: [i32; 3] = [0; 3];
            a[1];
             //^ Immutable
        }
    """)

    fun `test mutable array`() = testExpr("""
        fn main() {
            let mut a: [i32; 3] = [0; 3];
            a[1];
             //^ Inherited
        }
    """)

    fun `test immutable struct`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let x = Foo { a: 1 };
            (x.a);
              //^ Immutable
        }
    """)

    fun `test mutable struct`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut x = Foo { a: 1 };
            (x.a);
              //^ Inherited
        }
    """)

    fun `test mutable struct with immutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &foo;
            (x.a);
              //^ Immutable
        }
    """)

    fun `test mutable struct with mutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &mut foo;
            (x.a);
              //^ Declared
        }
    """)
}
