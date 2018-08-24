/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.infer.mutabilityCategory

class RsMemoryCategorizationTest : RsTestBase() {
    private fun testExpr(@Language("Rust") code: String, description: String = "") {
        InlineFile(code)
        check(description)
    }

    private fun check(description: String) {
        val (expr, expectedCategory) = findElementAndDataInEditor<RsExpr>()

        val category = expr.mutabilityCategory
        check(category.toString() == expectedCategory) {
            "Category mismatch. Expected: $expectedCategory, found: $category. $description"
        }
    }

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
              //^ Inherited
        }
    """)

    fun `test mutable struct with multiple references to immutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &mut &mut &foo;
            (x.a);
              //^ Immutable
        }
    """)

    fun `test mutable struct with multiple references to mutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = & & &mut foo;
            (x.a);
              //^ Inherited
        }
    """)

    fun `test const raw pointer`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let x = 5;
            let p = &x as *const i32;
            (*p);
             //^ Immutable
        }
    """)

    fun `test mut raw pointer`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut x = 5;
            let p = &mut x as *mut i32;
            (*p);
             //^ Declared
        }
    """)

    fun `test immutable self`() = testExpr("""
        struct Foo {}
        impl Foo {
            fn f(&self) {
                self;
                 //^ Immutable
            }
        }
    """)

    fun `test mutable self`() = testExpr("""
        struct Foo {}
        impl Foo {
            fn f(&mut self) {
                self;
                 //^ Declared
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test rvalue method call`() = testExpr("""
        fn main() {
          let v = vec![1];
          v.iter();
               //^ Declared
        }
    """)

    fun `test rvalue if expr`() = testExpr("""
        struct S {}
        fn main() {
          (if true { S } else { S });
                                 //^ Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test array`() = testExpr("""
        fn f(buf: &mut [u8]) {
            (buf[0]);
                 //^ Inherited
        }
    """)
}
