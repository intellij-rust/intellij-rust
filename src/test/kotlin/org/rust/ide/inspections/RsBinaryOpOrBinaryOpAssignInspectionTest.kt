/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsBinaryOpOrBinaryOpAssignInspectionTest : RsInspectionsTestBase(RsBinaryOpOrBinaryOpAssignInspection::class) {

    fun `test bit and op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `&=` cannot be applied to type `Test` [E0368]">a &= b</error>;
        }
    """)

    fun `test bit or op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `|=` cannot be applied to type `Test` [E0368]">a |= b</error>;
        }
    """)

    fun `test bit xor op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `^=` cannot be applied to type `Test` [E0368]">a ^= b</error>;
        }
    """)

    fun `test shl op assign`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary assignment operation `<<=` cannot be applied to type `Test` [E0368]">a <<= b</error>;
                }
            """)
    }

    fun `test shr op assign`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary assignment operation `>>=` cannot be applied to type `Test` [E0368]">a >>= b</error>;
                }
            """)
    }

    fun `test add op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `+=` cannot be applied to type `Test` [E0368]">a += b</error>;
        }
    """)

    fun `test sub op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `-=` cannot be applied to type `Test` [E0368]">a -= b</error>;
        }
    """)

    fun `test mul op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `*=` cannot be applied to type `Test` [E0368]">a *= b</error>;
        }
    """)

    fun `test div op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `/=` cannot be applied to type `Test` [E0368]">a /= b</error>;
        }
    """)

    fun `test rem op assign`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary assignment operation `%=` cannot be applied to type `Test` [E0368]">a %= b</error>;
        }
    """)

    fun `test bit and op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `&` cannot be applied to type `Test` [E0369]">a & b</error>;
        }
    """)

    fun `test bit xor op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `^` cannot be applied to type `Test` [E0369]">a ^ b</error>;
        }
    """)

    fun `test shl op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `<<` cannot be applied to type `Test` [E0369]">a << b</error>;
                }
            """)
    }

    fun `test shr op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `>>` cannot be applied to type `Test` [E0369]">a >> b</error>;
                }
            """)
    }

    fun `test add op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `+` cannot be applied to type `Test` [E0369]">a + b</error>;
        }
    """)

    fun `test sub op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `-` cannot be applied to type `Test` [E0369]">a - b</error>;
        }
    """)

    fun `test mul op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `*` cannot be applied to type `Test` [E0369]">a * b</error>;
        }
    """)

    fun `test div op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `/` cannot be applied to type `Test` [E0369]">a / b</error>;
        }
    """)

    fun `test rem or op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `%` cannot be applied to type `Test` [E0369]">a % b</error>;
        }
    """)

    fun `test lt op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `<` cannot be applied to type `Test` [E0369]">a < b</error>;
                }
            """)
    }

    fun `test lt eq op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `<=` cannot be applied to type `Test` [E0369]">a <= b</error>;
                }
            """)
    }

    fun `test gt op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `>` cannot be applied to type `Test` [E0369]">a > b</error>;
                }
            """)
    }

    fun `test gt eq op`() {
        // fix https://youtrack.jetbrains.com/issue/IDEA-186991
        if (ApplicationInfo.getInstance().build < BuildNumber.fromString("202.876")!!) return

        checkErrors("""
                struct Test(i32);

                fn foo(a: Test, b: Test) {
                    <error descr="binary operation `>=` cannot be applied to type `Test` [E0369]">a >= b</error>;
                }
            """)
    }

    fun `test eq op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `==` cannot be applied to type `Test` [E0369]">a == b</error>;
        }
    """)

    fun `test excl eq op`() = checkErrors("""
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `!=` cannot be applied to type `Test` [E0369]">a != b</error>;
        }
    """)

    fun `test impl the operator trait of the same name`() = checkErrors("""
        trait Add { }

        struct Test(i32);

        impl Add for Test { }

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `+` cannot be applied to type `Test` [E0369]">a + b</error>;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test impl operator trait`() = checkErrors("""
        use std::ops::Add;

        struct Test(i32);

        impl Add for Test {
            type Output = Test;

            fn add(self, rhs: Self) -> Self::Output {
                Test(self.0 + rhs.0)
            }
        }

        fn foo(a: Test, b: Test) {
            a + b;
        }
    """)
}
