/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ExpandMacros
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class ImplementOperatorTraitFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test add`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Cannot add `Test` to `Test` [E0369]"*/a + b/*error**/;
        }
    """)

    fun `test sub`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Cannot subtract `Test` from `Test` [E0369]"*/a - b/*error**/;
        }
    """)

    fun `test mul`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Cannot multiply `Test` by `Test` [E0369]"*/a * b/*error**/;
        }
    """)

    fun `test div`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Cannot divide `Test` by `Test` [E0369]"*/a / b/*error**/;
        }
    """)

    fun `test rem`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Cannot mod `Test` by `Test` [E0369]"*/a % b/*error**/;
        }
    """)

    fun `test eq`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `==` cannot be applied to type `Test` [E0369]"*/a == b/*error**/;
        }
    """)

    fun `test excl eq`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `!=` cannot be applied to type `Test` [E0369]"*/a != b/*error**/;
        }
    """)

    fun `test lt`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `<` cannot be applied to type `Test` [E0369]"*/a < b/*error**/;
        }
    """)

    fun `test lt eq`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `<=` cannot be applied to type `Test` [E0369]"*/a <= b/*error**/;
        }
    """)

    fun `test gt`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `>` cannot be applied to type `Test` [E0369]"*/a > b/*error**/;
        }
    """)

    fun `test gt eq`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary operation `>=` cannot be applied to type `Test` [E0369]"*/a >= b/*error**/;
        }
    """)

    fun `test bit and`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="No implementation for `Test & Test` [E0369]"*/a & b/*error**/;
        }
    """)

    fun `test bit or`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="No implementation for `Test | Test` [E0369]"*/a | b/*error**/;
        }
    """)

    fun `test bit xor`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="No implementation for `Test ^ Test` [E0369]"*/a ^ b/*error**/;
        }
    """)

    fun `test shl`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="No implementation for `Test << Test` [E0369]"*/a << b/*error**/;
        }
    """)

    fun `test shr`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="No implementation for `Test >> Test` [E0369]"*/a >> b/*error**/;
        }
    """)

    fun `test add assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `+=` cannot be applied to type `Test` [E0368]"*/a += b/*error**/;
        }
    """)

    fun `test sub assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `-=` cannot be applied to type `Test` [E0368]"*/a -= b/*error**/;
        }
    """)

    fun `test mul assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `*=` cannot be applied to type `Test` [E0368]"*/a *= b/*error**/;
        }
    """)

    fun `test div assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `/=` cannot be applied to type `Test` [E0368]"*/a /= b/*error**/;
        }
    """)

    fun `test rem assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `%=` cannot be applied to type `Test` [E0368]"*/a %= b/*error**/;
        }
    """)

    fun `test bit and assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `&=` cannot be applied to type `Test` [E0368]"*/a &= b/*error**/;
        }
    """)

    fun `test bit or assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `|=` cannot be applied to type `Test` [E0368]"*/a |= b/*error**/;
        }
    """)

    fun `test bit xor assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `^=` cannot be applied to type `Test` [E0368]"*/a ^= b/*error**/;
        }
    """)

    fun `test shl assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `<<=` cannot be applied to type `Test` [E0368]"*/a <<= b/*error**/;
        }
    """)

    fun `test shr assign`() = checkErrors("""
        struct Test {}
        fn func(a: Test, b: Test) {
            /*error descr="Binary assignment operation `>>=` cannot be applied to type `Test` [E0368]"*/a >>= b/*error**/;
        }
    """)

    @ExpandMacros
    fun `test add on i32`() = checkErrors("""
        fn func(a: i32, b: i32) -> i32 {
            a + b
        }
    """)

    fun `test exists impl of usual trait with same name`() = checkErrors("""
        trait Add {}
        struct Test {}
        impl Add for Test { }

        fn func(a: Test, b: Test) {
            /*error descr="Cannot add `Test` to `Test` [E0369]"*/a + b/*error**/;
        }
    """)

    fun `test exists impl of operator trait`() = checkErrors("""
        use std::ops::Add;

        struct Test {}
        impl Add for Test {
            type Output = ();
            fn add(self, rhs: Self) {}
        }

        fn func(a: Test, b: Test) {
            a + b;
        }
    """)

    fun `test exists two impls of operator trait`() = checkErrors("""
        use std::ops::Add;

        struct Test {}
        struct R1 {}
        struct R2 {}

        impl Add<R1> for Test {
            type Output = ();
            fn add(self, rhs: R1) {}
        }
        impl Add<R2> for Test {
            type Output = ();
            fn add(self, rhs: R2) {}
        }

        fn func(a: Test, b: R1) {
            a + b;
        }
    """)

    fun `test fix simple`() = checkFixByText("Implement `Add` trait", """
        struct Test {}

        fn func(a: Test, b: Test) {
            /*error descr="Cannot add `Test` to `Test` [E0369]"*/a + /*caret*/b/*error**/;
        }
    """, """
        use std::ops::Add;

        struct Test {}

        impl Add for Test {
            type Output = ();

            fn add(self, rhs: Self) -> Self::Output {
                todo!()
            }
        }

        fn func(a: Test, b: Test) {
            a + b;
        }
    """, preview = null)

    fun `test fix different operand types`() = checkFixByText("Implement `Add` trait", """
        struct Test {}

        fn func(a: Test, b: i32) {
            /*error descr="Cannot add `i32` to `Test` [E0369]"*/a + /*caret*/b/*error**/;
        }
    """, """
        use std::ops::Add;

        struct Test {}

        impl Add<i32> for Test {
            type Output = ();

            fn add(self, rhs: i32) -> Self::Output {
                todo!()
            }
        }

        fn func(a: Test, b: i32) {
            a + b;
        }
    """, preview = null)

    fun `test no fix for logic operator`() = checkFixIsUnavailable("Implement `Add` trait", """
        struct Foo {}

        fn func(a: Foo, b: Foo) {
            a &&/*caret*/ b
        }
    """)

    fun `test no fix when exist unbound generics`() = checkFixIsUnavailable("Implement `Add` trait", """
        struct Foo<T> { t: T }

        fn func<T>(a: Foo<T>, b: i32) {
            /*error descr="Cannot add `i32` to `Foo<T>` [E0369]"*/a +/*caret*/ b/*error**/
        }
    """)

    fun `test no fix for struct in other crate`() = checkFixIsUnavailableByFileTree("Implement `Add` trait", """
    //- lib.rs
        pub struct Foo {}
    //- main.rs
        use test_package::Foo;
        fn func(a: Foo, b: Foo) {
            /*error descr="Cannot add `Foo` to `Foo` [E0369]"*/a + /*caret*/b/*error**/;
        }
    """)
}
