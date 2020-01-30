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
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.adjustments
import org.rust.lang.core.types.infer.Adjustment

class RsAdjustmentTest : RsTestBase() {
    fun `test method without adjustements 1`() = testExpr("""
        struct S;
        impl S { fn foo(self) {} }
        fn main() {
            S.foo();
        } //^
    """)

    fun `test method without adjustements 2`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (&S).foo();
        } //^
    """)

    fun `test method without adjustements 3`() = testExpr("""
        struct S;
        impl S { fn foo(&mut self) {} }
        fn main() {
            let mut a = S;
            (&mut a).foo();
        } //^
    """)

    fun `test method borrow`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            S.foo();
        } //^ borrow(&S)
    """)

    fun `test method borrow mut`() = testExpr("""
        struct S;
        impl S { fn foo(&mut self) {} }
        fn main() {
            S.foo();
        } //^ borrow(&mut S)
    """)

    fun `test method deref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (&&S).foo();
        } //^ deref(&S)
    """)

    fun `test method deref 2`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (&&&S).foo();
        } //^ deref(&&S), deref(&S)
    """)

    fun `test method deref mut and borrow ref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            let mut a = S;
            (&mut a).foo();
        } //^ deref(S), borrow(&S)
    """)

    fun `test method deref mut 2 and borrow ref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            let mut a = S;
            (&mut &mut a).foo();
        } //^ deref(&mut S), deref(S), borrow(&S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method deref Box and borrow ref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (Box::new(S)).foo();
        } //^ deref(S), borrow(&S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method no adjustments with Box self`() = testExpr("""
        struct S;
        impl S { fn foo(self: Box<Self>) {} }
        fn main() {
            (Box::new(S)).foo();
        } //^
    """)

    fun `test field without adjustments`() = testExpr("""
        struct S {
            f: i32
        }
        fn foo(s: S) {
            s.f;
        } //^
    """)

    fun `test field deref`() = testExpr("""
        struct S {
            f: i32
        }
        fn foo(s: &S) {
            s.f;
        } //^ deref(S)
    """)

    fun `test field deref 2`() = testExpr("""
        struct S {
            f: i32
        }
        fn foo(s: &&S) {
            s.f;
        } //^ deref(&S), deref(S)
    """)

    fun `test tuple field without adjustments`() = testExpr("""
        struct S(i32);
        fn foo(s: S) {
            s.0;
        } //^
    """)

    fun `test tuple field deref`() = testExpr("""
        struct S(i32);
        fn foo(s: &S) {
            s.0;
        } //^ deref(S)
    """)

    fun `test tuple field deref 2`() = testExpr("""
        struct S(i32);
        fn foo(s: &&S) {
            s.0;
        } //^ deref(&S), deref(S)
    """)

    fun `test no reference coercion`() = testExpr("""
        struct S;
        fn main() {
            let _: &S = &S;
        }             //^
    """)

    fun `test reference coercion`() = testExpr("""
        struct S;
        fn main() {
            let _: &S = &&S;
        }             //^ deref(&S), deref(S), borrow(&S)
    """)

    // It's weird, but this is how rustc works
    fun `test reference coercion mut mut`() = testExpr("""
        struct S;
        fn main() {
            let mut a = S;
            let _: &mut S = &mut a;
        }                 //^ deref(S), borrow(&mut S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test reference coercion box`() = testExpr("""
        struct S;
        fn main() {
            let _: &S = &Box::new(S);
        }             //^ deref(Box<S>), deref(S), borrow(&S)
    """)

    fun `test reference coercion in field shorthand`() = testFieldShorthand("""
        struct S;
        struct W<'a> {
            f: &'a S
        }
        fn main() {
            let f = &&S;
            let _ = W {
                f
            };//^ deref(&S), deref(S), borrow(&S)
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test index expr borrow`() = testExpr("""
        fn main() {
            let v = Vec::<i32>::new();
            v[0];
        } //^ borrow(&Vec<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test index expr without adjustments`() = testExpr("""
        fn main() {
            let v = &Vec::<i32>::new();
            v[0];
        } //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test index expr deref`() = testExpr("""
        fn main() {
            let v = &&Vec::<i32>::new();
            v[0];
        } //^ deref(&Vec<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test index expr deref 2`() = testExpr("""
        fn main() {
            let v = &&&Vec::<i32>::new();
            v[0];
        } //^ deref(&&Vec<i32>), deref(&Vec<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test index expr deref borrow`() = testExpr("""
        fn main() {
            let mut a = Vec::<i32>::new();
            let v = &mut a;
            v[0];
        } //^ deref(Vec<i32>), borrow(&Vec<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test assign index expr borrow mut`() = testExpr("""
        fn main() {
            let mut v = Vec::<i32>::new();
            v[0] = 1;
        } //^ borrow(&mut Vec<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test assign index expr without adjustments`() = testExpr("""
        fn main() {
            let mut a = Vec::<i32>::new();
            let v = &mut a;
            v[0] = 1;
        } //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test array index expr without adjustments`() = testExpr("""
        fn main() {
            let a = [1, 2, 3];
            a[0];
        } //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test array assign index expr without adjustments`() = testExpr("""
        fn main() {
            let mut a = [1, 2, 3];
            a[0] = 1;
        } //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test array index expr deref`() = testExpr("""
        fn main() {
            let a = &[1, 2, 3];
            a[0];
        } //^ deref([i32; 3])
    """)

    private fun testExpr(@Language("Rust") code: String) {
        InlineFile(code)
        val (expr, expectedAdjustments) = findElementAndDataInEditor<RsExpr>()
        checkAdjustments(expr, expectedAdjustments)
    }

    private fun testFieldShorthand(@Language("Rust") code: String) {
        InlineFile(code)
        val (expr, expectedAdjustments) = findElementAndDataInEditor<RsStructLiteralField>()
        checkAdjustments(expr, expectedAdjustments)
    }

    private fun checkAdjustments(expr: RsElement, expectedAdjustments: String) {
        val adjustments = when (expr) {
            is RsExpr -> expr.adjustments
            is RsStructLiteralField -> expr.adjustments
            else -> error("Unsupported element: $expr")
        }
        val adjustmentsStr = adjustments.joinToString(", ") {
            when (it) {
                is Adjustment.Deref -> "deref(${it.target})"
                is Adjustment.BorrowReference -> "borrow(${it.target})"
            }
        }
        assertEquals(expectedAdjustments, adjustmentsStr)
    }
}
