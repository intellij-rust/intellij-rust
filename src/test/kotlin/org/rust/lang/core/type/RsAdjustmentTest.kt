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
    fun `test assign index expr without adjustments`() = testExpr("""
        fn main() {
            let mut a = Vec::<i32>::new();
            let v = &mut a;
            v[0] = 1;
        } //^ deref(Vec<i32>), borrow(&mut Vec<i32>)
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

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test array index expr with range`() = testExpr("""
        fn main() {
            let a = &[1, 2, 3];
            a[..];
        } //^ deref([i32; 3]), borrow(&[i32; 3]), unsize(&[i32])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 1`() = testExpr("""
        fn f(buf: &mut [u8]) {
            buf[0] = 1;
        }  //^ deref([u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 2`() = testExpr("""
        fn f(buf: &mut [u8]) {
            (buf[0]) = 1;
        }   //^ deref([u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 3`() = testExpr("""
        fn f(buf: &mut [&mut [u8]]) {
            buf[0][0] = 1;
        }  //^ deref([&mut [u8]])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 1`() = testExpr("""
        fn f(buf: &mut [u8]) {
            let _ = &mut (buf[0]);
        }                //^ deref([u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 2`() = testExpr("""
        fn f(buf: &mut [u8]) {
            let ref mut a = (buf[0]);
        }                   //^ deref([u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 3`() = testExpr("""
        struct S;
        impl S { fn foo(&mut self) {} }
        fn f(buf: &mut [S]) {
            buf[0].foo();
        }  //^ deref([S])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 4`() = testExpr("""
        fn main() {
            let mut a = [1, 2, 3];
            let b = a[..].get_mut(1);
        }         //^ borrow(&mut [i32; 3]), unsize(&mut [i32])
    """)

    @Language("Rust")
    private val indexable = """
        #[lang = "index"]
        pub trait Index<Idx> { type Output; }
        #[lang = "index_mut"]
        pub trait IndexMut<Idx>: Index<Idx> {}

        struct Indexable<T>;

        impl<T> Index<usize> for Indexable<T> { type Output = T; }
        impl<T> IndexMut<usize> for Indexable<T> {}
    """.trimIndent()

    fun `test overloaded index assign 1`() = testExpr("""
        $indexable
        fn f(buf: &mut Indexable<u8>) {
            buf[0] = 1;
        }  //^ deref(Indexable<u8>), borrow(&mut Indexable<u8>)
    """)

    fun `test overloaded index assign 2`() = testExpr("""
        $indexable
        fn f(buf: &mut Indexable<u8>) {
            (buf[0]) = 1;
        }   //^ deref(Indexable<u8>), borrow(&mut Indexable<u8>)
    """)

    fun `test overloaded index assign 3`() = testExpr("""
        $indexable
        fn f(buf: &mut Indexable<&mut Indexable<u8>>) {
            buf[0][0] = 1;
        }  //^ deref(Indexable<&mut Indexable<u8>>), borrow(&mut Indexable<&mut Indexable<u8>>)
    """)

    fun `test overloaded index assign 4`() = testExpr("""
        $indexable
        fn f(mut buf: Indexable<u8>) {
            buf[0] = 1;
        }   //^ borrow(&mut Indexable<u8>)
    """)

    fun `test overloaded index mut 1`() = testExpr("""
        $indexable
        fn f(buf: &mut Indexable<u8>) {
            let _ = &mut (buf[0]);
        }                //^ deref(Indexable<u8>), borrow(&mut Indexable<u8>)
    """)

    fun `test overloaded index mut 2`() = testExpr("""
        $indexable
        fn f(buf: &mut Indexable<u8>) {
            let ref mut a = (buf[0]);
        }                   //^ deref(Indexable<u8>), borrow(&mut Indexable<u8>)
    """)

    fun `test overloaded index with &mut method call`() = testExpr("""
        $indexable
        struct S;
        impl S { fn foo(&mut self) {} }
        fn f(buf: &mut Indexable<S>) {
            buf[0].foo();
        }  //^ deref(Indexable<S>), borrow(&mut Indexable<S>)
    """)

    fun `test overloaded index 1`() = testExpr("""
        $indexable
        fn f(buf: Indexable<u8>) {
            buf[0];
        }  //^ borrow(&Indexable<u8>)
    """)

    fun `test overloaded index 2`() = testExpr("""
        $indexable
        fn f(buf: &Indexable<u8>) {
            buf[0];
        }  //^ deref(Indexable<u8>), borrow(&Indexable<u8>)
    """)

    fun `test overloaded index 3`() = testExpr("""
        $indexable
        fn f(buf: &&Indexable<u8>) {
            buf[0];
        }  //^ deref(&Indexable<u8>), deref(Indexable<u8>), borrow(&Indexable<u8>)
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
                is Adjustment.Unsize -> "unsize(${it.target})"
            }
        }
        assertEquals(expectedAdjustments, adjustmentsStr)
    }
}
