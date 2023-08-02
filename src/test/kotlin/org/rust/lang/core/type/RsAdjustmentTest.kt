/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.MinRustcVersion
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.adjustments
import org.rust.lang.core.types.infer.Adjustment
import org.rust.lang.core.types.ty.Mutability

@MinRustcVersion("1.42.0")
class RsAdjustmentTest : RsTestBase() {
    fun `test never to any`() = testExpr("""
        fn main() {
            let a: i32 = never();
        }                   //^ neverToAny(i32)
        fn never() -> ! { panic!() }
    """)

    fun `test method without adjustments 1`() = testExpr("""
        struct S;
        impl S { fn foo(self) {} }
        fn main() {
            S.foo();
        } //^
    """)

    fun `test method without adjustments 2`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (&S).foo();
        } //^
    """)

    fun `test method without adjustments 3`() = testExpr("""
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
        }  //^ deref(&S)
    """)

    fun `test method deref 2`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (&&&S).foo();
        }  //^ deref(&&S), deref(&S)
    """)

    fun `test method deref mut and borrow ref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            let mut a = S;
            (&mut a).foo();
        }  //^ deref(S), borrow(&S)
    """)

    fun `test method deref mut 2 and borrow ref`() = testExpr("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            let mut a = S;
            (&mut &mut a).foo();
        }  //^ deref(&mut S), deref(S), borrow(&S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method deref Rc and borrow ref`() = testExpr("""
        use std::rc::Rc;
        struct S;
        impl S { fn foo(&self) {} }
        fn main() {
            (Rc::new(S)).foo();
        }         //^ overloaded_deref(S), borrow(&S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method deref and move Rc`() = testExpr("""
        use std::rc::Rc;
        struct S;
        impl S { fn foo(self) {} }
        fn main() {
            (Rc::new(S)).foo();
        }         //^ overloaded_deref(S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method deref Rc and borrow ref mut`() = testExpr("""
        use std::rc::Rc;
        struct S;
        impl S { fn foo(&mut self) {} }
        fn main() {
            (Rc::new(S)).foo();
        }         //^ overloaded_deref_mut(S), borrow(&mut S)
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

    fun `test reference coercion mut`() = testExpr("""
        struct S;
        fn main() {
            let mut a = S;
            let _: &S = &mut a;
        }             //^ deref(S), borrow(&S)
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
    fun `test reference coercion Rc`() = testExpr("""
        use std::rc::Rc;
        struct S;
        fn main() {
            let _: &S = &Rc::new(S);
        }             //^ deref(Rc<S>), overloaded_deref(S), borrow(&S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test reference coercion Rc mut`() = testExpr("""
        use std::rc::Rc;
        struct S;
        fn main() {
            let _: &mut S = &mut Rc::new(S);
        }                 //^ deref(Rc<S>), overloaded_deref_mut(S), borrow(&mut S)
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

    fun `test reference to const pointer coercion`() = testExpr("""
        struct S;
        fn main() {
            let _: *const S = &S;
        }                   //^ deref(S), borrow(*const S)
    """)

    fun `test mut reference to const pointer coercion`() = testExpr("""
        struct S;
        fn main() {
            let _: *const S = &mut S;
        }                   //^ deref(S), borrow(*const S)
    """)

    fun `test mut reference to mut pointer coercion`() = testExpr("""
        struct S;
        fn main() {
            let _: *mut S = &mut S;
        }                 //^ deref(S), borrow(*mut S)
    """)

    fun `test mut pointer to const pointer coercion`() = testExpr("""
        struct S;
        fn main() {
            let a: *mut S = &mut S;
            let _: *const S = a;
        }                   //^ mutToConstPtr(*const S)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test assign index expr without adjustments`() = testExpr("""
        fn main() {
            let mut a = Vec::<i32>::new();
            let v = &mut a;
            v[0] = 1;
        } //^ deref(Vec<i32, Global>), borrow(&mut Vec<i32, Global>)
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
        } //^ deref([i32; 3]), borrow(&[i32; 3])
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
        }         //^ borrow(&mut [i32; 3])
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

    fun `test builtin explicit deref rvalue 1`() = testExpr("""
        fn f(a: &i32) {
            let _ = *a;
        }          //^
    """)

    fun `test builtin explicit deref rvalue 2`() = testExpr("""
        unsafe fn f(a: *const i32) {
            let _ = *a;
        }          //^
    """)

    fun `test builtin explicit deref lvalue 1`() = testExpr("""
        fn f(a: &mut i32) {
            *a = 1;
        }  //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded explicit deref rvalue`() = testExpr("""
        use std::rc::Rc;
        fn f(a: Rc<i32>) {
            let _ = &*a;
        }           //^ borrow(&Rc<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded explicit deref lvalue 1`() = testExpr("""
        use std::rc::Rc;
        fn f(a: Rc<i32>) {
            *a = 1;
        }  //^ borrow(&mut Rc<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded explicit deref lvalue 2`() = testExpr("""
        use std::rc::Rc;
        fn f(a: Rc<i32>) {
            *(a) = 1;
        }   //^ borrow(&mut Rc<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded explicit deref lvalue 3`() = testExpr("""
        use std::rc::Rc;
        fn f(a: Rc<i32>) {
            (*(a)) = 1;
        }    //^ borrow(&mut Rc<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice ref`() = testExpr("""
        fn main() {
            let a: &[u8] = &[1, 2, 3];
        }                //^ deref([u8; 3]), borrow(&[u8; 3]), unsize(&[u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice ref mut`() = testExpr("""
        fn main() {
            let a: &mut [u8] = &mut [1, 2, 3];
        }                    //^ deref([u8; 3]), borrow(&mut [u8; 3]), unsize(&mut [u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice ref mut to ref`() = testExpr("""
        fn main() {
            let a: &[u8] = &mut [1, 2, 3];
        }                //^ deref([u8; 3]), borrow(&[u8; 3]), unsize(&[u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice ref mut to ref wrapped`() = testExpr("""
        fn main() {
            let a: &[u8] = (&mut [1, 2, 3]);
        }                 //^ deref([u8; 3]), borrow(&[u8; 3]), unsize(&[u8])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice Box`() = testExpr("""
        fn main() {
            let a: Box<[u8]> = Box::new([1, 2, 3]);
        }                            //^ unsize(Box<[u8], Global>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice method call`() = testExpr("""
        fn main() {
            let a = [1, 2, 3];
            a.len();
        } //^ borrow(&[i32; 3]), unsize(&[i32])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test unsize array to slice method call mut`() = testExpr("""
        fn main() {
            let mut a = [1, 2, 3];
            a.first_mut();
        } //^ borrow(&mut [i32; 3]), unsize(&mut [i32])
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test closure to function pointer`() = testExpr("""
        fn main() {
            let a: fn() = || {};
                        //^ closureFnPointer(fn())
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test closure to function pointer with param`() = testExpr("""
        fn main() {
            let a: fn(i32) = |b: i32| {};
                           //^ closureFnPointer(fn(i32))
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test closure to function pointer with return value`() = testExpr("""
        fn main() {
            let a: fn() -> i32 = || { 0i32 };
                               //^ closureFnPointer(fn() -> i32)
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test closure to function pointer with param and return value`() = testExpr("""
        fn main() {
            let a: fn(i32) -> i64 = |b: i32| { 0i64 };
                                  //^ closureFnPointer(fn(i32) -> i64)
        }

    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test closure to unsafe function pointer`() = testExpr("""
        fn main() {
            let a: unsafe fn() = || {};
                               //^ closureFnPointer(unsafe fn())
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dont coerce closure to another closure`() = testExpr("""
        fn main() {
            let mut a = || {};
            a = || {};
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dont coerce closure to function def`() = testExpr("""
        fn foo() {}
        fn main() {
            let mut a = foo;
            a = || {};
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test function def to function pointer`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
                        //^ reifyFnPointer(fn())
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test enum variant to function pointer`() = testExpr("""
        enum X {
            A(i32)
        }
        fn main() {
            let a: fn(i32) -> X = X::A;
                                //^ reifyFnPointer(fn(i32) -> X)
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test struct constructor to function pointer`() = testExpr("""
        struct S(i32);

        fn main() {
            let a: fn(i32) -> S = S;
                                //^ reifyFnPointer(fn(i32) -> S)
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test function def to unsafe function pointer`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: unsafe fn() = foo;
                               //^ reifyFnPointer(fn()), unsafeFnPointer(unsafe fn())
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dont coerce function to another function`() = testExpr("""
        fn foo() {}
        fn bar() {}
        fn main() {
            let mut a = foo;
            a = bar;
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dont coerce function to closure`() = testExpr("""
        fn foo() {}
        fn main() {
            let mut a = || {};
            a = foo;
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test function pointer to unsafe function pointer`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
            let b: unsafe fn() = a;
                               //^ unsafeFnPointer(unsafe fn())
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no coercion needed for function pointer to function pointer`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
            let b: fn() = a;
                        //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test don't coerce function pointer to function def`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
            let mut b = foo;
            b = a;
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test don't coerce function pointer to closure`() = testExpr("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
            let mut b = || {};
            b = a;
              //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in arithmetic binop 1`() = testExpr("""
        fn main() {
            let a = 2 + 2;
        }         //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in arithmetic binop 2`() = testExpr("""
        fn main() {
            let a = 2 + 2;
        }             //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in comparison binop 1`() = testExpr("""
        fn main() {
            let a = 2 < 2;
        }         //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in comparison binop 2`() = testExpr("""
        fn main() {
            let a = 2 < 2;
        }             //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in logical binop 1`() = testExpr("""
        fn main() {
            let a = true || false;
        }         //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in logical binop 2`() = testExpr("""
        fn main() {
            let a = true || false;
        }                 //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in arithmetic assignment binop 1`() = testExpr("""
        fn main() {
            let mut a = 1;
            a += 2;
        } //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test built-in arithmetic assignment binop 2`() = testExpr("""
        fn main() {
            let mut a = 1;
            a += 2;
        }      //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded arithmetic binop 1`() = testExpr("""
        fn main() {
            let a = std::num::Wrapping(1);
            let b = a + a;
        }         //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded arithmetic binop 2`() = testExpr("""
        fn main() {
            let a = std::num::Wrapping(1);
            let b = a + a;
        }             //^
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded comparison binop 1`() = testExpr("""
        fn main() {
            let a = std::num::Wrapping(1);
            let b = a < a;
        }         //^ borrow(&Wrapping<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded comparison binop 2`() = testExpr("""
        fn main() {
            let a = std::num::Wrapping(1);
            let b = a < a;
        }             //^ borrow(&Wrapping<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded arithmetic assignment binop 1`() = testExpr("""
        fn main() {
            let mut a = std::num::Wrapping(1);
            a += std::num::Wrapping(2);
        } //^ borrow(&mut Wrapping<i32>)
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test overloaded arithmetic assignment binop 2`() = testExpr("""
        fn main() {
            let mut a = std::num::Wrapping(1);
            let b = std::num::Wrapping(2);
            a += b;
        }      //^
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
        val adjustmentsStr = adjustments.joinToString("\n") {
            when (it) {
                is Adjustment.NeverToAny -> "neverToAny(${it.target})"
                is Adjustment.Deref -> when (it.overloaded) {
                    Mutability.MUTABLE -> {
                        "overloaded_deref_mut(${it.target})"
                    }
                    Mutability.IMMUTABLE -> {
                        "overloaded_deref(${it.target})"
                    }
                    null -> {
                        "deref(${it.target})"
                    }
                }
                is Adjustment.BorrowReference -> "borrow(${it.target})"
                is Adjustment.BorrowPointer -> "borrow(${it.target})" // FIXME: should be different from BorrowReference
                is Adjustment.MutToConstPointer -> "mutToConstPtr(${it.target})"
                is Adjustment.Unsize -> "unsize(${it.target})"
                is Adjustment.ClosureFnPointer -> "closureFnPointer(${it.target})"
                is Adjustment.ReifyFnPointer -> "reifyFnPointer(${it.target})"
                is Adjustment.UnsafeFnPointer -> "unsafeFnPointer(${it.target})"
            }
        }
        assertEquals(expectedAdjustments.replace("), ", ")\n"), adjustmentsStr)
    }
}
