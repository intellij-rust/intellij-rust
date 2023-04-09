/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import junit.framework.ComparisonFailure
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.refactoring.RsInlineTestBase

class RsInlineFunctionTest2 : RsInlineTestBase() {

    fun `test simple`() = doTest("""
        fn main() {
            foo();
        }
        fn /*caret*/foo() {
            println!("");
        }
    """, """
        fn main() {
            println!("");
        }
    """)

    fun `test call used as statement (function is simple expression)`() = doTest("""
        fn main() {
            foo();
        }
        fn /*caret*/foo() {
            2 + 2
        }
    """, """
        fn main() {
            2 + 2;
        }
    """)

    fun `test call used as statement`() = doTest("""
        fn main() {
            println!(0);
            foo();
            println!(3);
        }
        fn /*caret*/foo() {
            println!(1);
            println!(2);
        }
    """, """
        fn main() {
            println!(0);
            println!(1);
            println!(2);
            println!(3);
        }
    """)

    fun `test call used as tail expr (function has tail expr)`() = doTest("""
        fn main() {
            foo()
        }
        fn /*caret*/foo() {
            println!(2);
            bar()
        }
    """, """
        fn main() {
            println!(2);
            bar()
        }
    """)

    fun `test call used as tail expr (function has return statement)`() = doTest("""
        fn main() {
            foo()
        }
        fn /*caret*/foo() {
            println!(2);
            return bar();
        }
    """, """
        fn main() {
            println!(2);
            bar()
        }
    """)

    fun `test call used as tail expr (function has no tail expr)`() = doTest("""
        fn main() {
            foo()
        }
        fn /*caret*/foo() {
            println!(2);
            bar();
        }
    """, """
        fn main() {
            println!(2);
            bar();
        }
    """)

    fun `test call used as argument (function is simple expression)`() = doTest("""
        fn main() {
            bar(foo());
        }
        fn /*caret*/foo() -> i32 {
            2 + 2
        }
    """, """
        fn main() {
            bar(2 + 2);
        }
    """)

    fun `test call used as argument (function is complex expression)`() = doTest("""
        fn main() {
            bar(foo());
        }
        fn /*caret*/foo() -> i32 {
            bar1()
                .bar2()
                .bar3()
        }
    """, """
        fn main() {
            bar(bar1()
                .bar2()
                .bar3());
        }
    """)

    fun `test call used as argument`() = doTest("""
        fn main() {
            bar(foo());
        }
        fn /*caret*/foo() -> i32 {
            println!(1);
            2 + 2
        }
    """, """
        fn main() {
            println!(1);
            bar(2 + 2);
        }
    """)

    fun `test call used as lambda body (function is simple expression)`() = doTest("""
        fn main() {
            let _ = || foo();
        }
        fn /*caret*/foo() {
            2 + 2
        }
    """, """
        fn main() {
            let _ = || 2 + 2;
        }
    """)

    fun `test call used as lambda body`() = doTest("""
        fn main() {
            let _ = || foo();
        }
        fn /*caret*/foo() {
            println!(1);
            println!(2);
        }
    """, """
        fn main() {
            let _ = || {
                println!(1);
                println!(2);
            };
        }
    """)

    fun `test call used as match arm body`() = doTest("""
        fn main() {
            match 0 {
                _ => foo(),
            }
        }
        fn /*caret*/foo() {
            println!(1);
            println!(2);
        }
    """, """
        fn main() {
            match 0 {
                _ => {
                    println!(1);
                    println!(2);
                }
            }
        }
    """)

    fun `test call used as constant initializer`() = doTest("""
        const C: i32 = foo();
        const fn /*caret*/foo() -> i32 {
            bar();
            1
        }
    """, """
        const C: i32 = {
            bar();
            1
        };
    """)

    fun `test call used as variable initialization (function is simple expression)`() = doTest("""
        fn main() {
            let a = foo();
        }
        fn /*caret*/foo() {
            2 + 2
        }
    """, """
        fn main() {
            let a = 2 + 2;
        }
    """)

    fun `test call used as variable initialization (return value is complex)`() = doTest("""
        fn main() {
            let a = foo();
        }
        fn /*caret*/foo() {
            println!(1);
            2 + 2
        }
    """, """
        fn main() {
            println!(1);
            let a = 2 + 2;
        }
    """)

    fun `test call used as variable initialization (return value is variable)`() = doTest("""
        fn main() {
            let a = foo();
        }
        fn /*caret*/foo() {
            let b = 2 + 2;
            println!(1);
            b
        }
    """, """
        fn main() {
            let a = 2 + 2;
            println!(1);
        }
    """)

    fun `test call used as variable initialization (return value is variable) 2`() = doTest("""
        fn main() {
            let a = foo();
        }
        fn /*caret*/foo() {
            let a = 2;
            let a = a + 2;
            println!(1);
            a
        }
    """, """
        fn main() {
            let a = 2;
            let a = a + 2;
            println!(1);
        }
    """)

    fun `test call used as variable initialization (return value is variable, name conflict)`() = doTest("""
        fn main() {
            let a = foo();
        }
        fn /*caret*/foo() {
            let b = 2 + 2;
            let a = 3;
            println!(1);
            b
        }
    """, """
        fn main() {
            let a = 2 + 2;
            let a1 = 3;
            println!(1);
        }
    """)

    fun `test call used as variable initialization (return value is matched tuple)`() = doTest("""
        fn main() {
            let (a1, (a2, a3)) = foo();
        }
        fn /*caret*/foo() {
            let b3 = 3 + 3;
            let b2 = 2 + 2;
            let b1 = 1 + 1;
            println!(1);
            (b1, (b2, b3))
        }
    """, """
        fn main() {
            let a3 = 3 + 3;
            let a2 = 2 + 2;
            let a1 = 1 + 1;
            println!(1);
        }
    """)

    fun `test call used as mutable variable initialization (return value is variable)`() = doTest("""
        fn main() {
            let mut a = foo();
        }
        fn /*caret*/foo() {
            let b = 2 + 2;
            println!(1);
            b
        }
    """, """
        fn main() {
            let mut a = 2 + 2;
            println!(1);
        }
    """)

    fun `test call used as if guard`() = doTest("""
        fn main() {
            match 0 {
                0 if foo() => 1,
                _ => 2,
            }
        }
        fn /*caret*/foo() -> bool {
            println!("1");
            true
        }
    """, """
        fn main() {
            println!("1");
            match 0 {
                0 if true => 1,
                _ => 2,
            }
        }
    """)

    fun `test call used in another modules`() = doTest("""
        mod mod1 {
            use crate::inner::foo;
            fn usage() { foo(); }
        }
        mod mod2 {
            use crate::inner::{foo, foo2};
            fn usage() { foo(); }
        }
        mod mod3 {
            use crate::{inner::foo, foo2, foo3};
            fn usage() { foo(); }
        }
        mod mod4 {
            use crate::inner::{foo, foo2, foo3};
            fn usage() { foo(); }
        }
        mod mod5 {
            use crate::{inner::foo, foo2, foo3};
            fn usage() { foo(); }
        }
        mod inner {
            pub fn /*caret*/foo() {
                bar();
            }
        }
    """, """
        mod mod1 {
            fn usage() { bar(); }
        }
        mod mod2 {
            use crate::inner::foo2;
            fn usage() { bar(); }
        }
        mod mod3 {
            use crate::{foo2, foo3};
            fn usage() { bar(); }
        }
        mod mod4 {
            use crate::inner::{foo2, foo3};
            fn usage() { bar(); }
        }
        mod mod5 {
            use crate::{foo2, foo3};
            fn usage() { bar(); }
        }
        mod inner {}
    """)

    fun `test call used as object used for method call`() = doTest("""
        fn main() {
            let a = foo().abs();
        }
        fn /*caret*/foo() {
            2 + 2
        }
    """, """
        fn main() {
            let a = (2 + 2).abs();
        }
    """)

    fun `test call used as object used for field access`() = doTest("""
        fn main() {
            let a = foo().abs;
        }
        fn /*caret*/foo() {
            2 + 2
        }
    """, """
        fn main() {
            let a = (2 + 2).abs;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test call used as try expr (return value is Some)`() = doTest("""
        fn test() -> Option<i32> {
            let x = foo()?;
            Some(x)
        }
        fn /*caret*/foo() -> Option<i32> {
            Some(0)
        }
    """, """
        fn test() -> Option<i32> {
            let x = 0;
            Some(x)
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test call used as try expr (return value is Ok)`() = doTest("""
        fn test() -> Result<i32, ()> {
            let x = foo()?;
            Ok(x)
        }
        fn /*caret*/foo() -> Result<i32, ()> {
            Ok(0)
        }
    """, """
        fn test() -> Result<i32, ()> {
            let x = 0;
            Ok(x)
        }
    """)

    fun `test substitute simple expression arguments (single usage)`() = doTest("""
        fn main() {
            foo(1, 2, "a", 2 + 2);
        }
        fn /*caret*/foo(p1: i32, p2: i32, p3: &str, p4: i32) {
            println!(p1, p2, p3, p4);
        }
    """, """
        fn main() {
            println!(1, 2, "a", 2 + 2);
        }
    """)

    fun `test substitute simple expression arguments (literals)`() = doTest("""
        fn main() {
            foo(0, 1, "", "a", 2 + 2);
        }
        fn /*caret*/foo(p1: i32, p2: i32, p3: &str, p4: &str, p5: i32) {
            println!(p1, p2, p3, p4, p5);
            println!(p1, p2, p3, p4, p5);
        }
    """, """
        fn main() {
            let p2 = 1;
            let p4 = "a";
            let p5 = 2 + 2;
            println!(0, p2, "", p4, p5);
            println!(0, p2, "", p4, p5);
        }
    """)

    fun `test substitute binary expression argument`() = doTest("""
        fn main() {
            foo(2 + 2);
        }
        fn /*caret*/foo(p: i32) {
            p.abs();
        }
    """, """
        fn main() {
            (2 + 2).abs();
        }
    """)

    fun `test substitute plain variable argument`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
        }
        fn /*caret*/foo(p: i32) {
            println!(p);
        }
    """, """
        fn main() {
            let a = 1;
            println!(a);
        }
    """)

    fun `test substitute field access argument`() = expect<ComparisonFailure> {
    doTest("""
        fn main() {
            let s = Foo { field: 0 };
            foo(s.field);
        }
        fn /*caret*/foo(p: i32) {
            println!(p);
        }
    """, """
        fn main() {
            let s = Foo { field: 0 };
            println!(s.field);
        }
    """)
    }

    fun `test substitute ref variable argument`() = doTest("""
        fn main() {
            let a = 1;
            foo(&a);
        }
        fn /*caret*/foo(p: &i32) {
            bar(p);
            p.abs();
        }
    """, """
        fn main() {
            let a = 1;
            bar(&a);
            a.abs();
        }
    """)

    fun `test substitute ref mut variable argument`() = doTest("""
        fn main() {
            let mut a = 1;
            foo(&mut a);
        }
        fn /*caret*/foo(p: &mut i32) {
            bar(p);
            p.abs();
            *p = 1;
        }
    """, """
        fn main() {
            let mut a = 1;
            bar(&mut a);
            a.abs();
            a = 1;
        }
    """)

    fun `test substitute argument without usages (no side effects)`() = doTest("""
        fn main() {
            foo(1);
        }
        fn /*caret*/foo(p: i32) {
            println!();
        }
    """, """
        fn main() {
            println!();
        }
    """)

    fun `test substitute argument without usages (has side effects)`() = doTest("""
        fn main() {
            foo(bar());
        }
        fn /*caret*/foo(p: i32) {
            println!();
        }
    """, """
        fn main() {
            let p = bar();
            println!();
        }
    """)

    fun `test substitute plain variable argument to mut parameter 1`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
        }
        fn /*caret*/foo(mut p: i32) {
            p = 2;
            bar(p);
        }
    """, """
        fn main() {
            let mut a = 1;
            a = 2;
            bar(a);
        }
    """)

    fun `test substitute plain variable argument to mut parameter 2`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
            bar(a);
        }
        fn /*caret*/foo(mut p: i32) {
            p = 2;
            bar(p);
        }
    """, """
        fn main() {
            let a = 1;
            let mut p = a;
            p = 2;
            bar(p);
            bar(a);
        }
    """)

    fun `test name conflict of let declaration in function body with outer scope`() = doTest("""
        fn main() {
            let a = 1;
            foo();
            println!(a);
        }
        fn /*caret*/foo() {
            let a = 2;
            println!(a);
        }
    """, """
        fn main() {
            let a = 1;
            let a1 = 2;
            println!(a1);
            println!(a);
        }
    """)

    fun `test name conflict of substituted argument with binding in function body`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
        }
        fn /*caret*/foo(p: i32) {
            if let a = 1 {
                println!(p);
            }
        }
    """, """
        fn main() {
            let a = 1;
            let p = a;
            if let a = 1 {
                println!(p);
            }
        }
    """)

    fun `test keep nested bindings name in function body if possible`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
        }
        fn /*caret*/foo(p: i32) {
            if let a = 1 {
                println!(a);
            }
        }
    """, """
        fn main() {
            let a = 1;
            if let a = 1 {
                println!(a);
            }
        }
    """)

    fun `test name conflict of parameter name with outer scope`() = doTest("""
        fn main() {
            let x = 1;
            foo(bar());
            println!(x);
        }
        fn /*caret*/foo(x: i32) {
            println!(x);
        }
    """, """
        fn main() {
            let x = 1;
            let x1 = bar();
            println!(x1);
            println!(x);
        }
    """)

    fun `test function single statement is macro`() = doTest("""
        fn main() {
            foo();
        }

        fn /*caret*/foo() {
            bar!();
        }
    """, """
        fn main() {
            bar!();
        }
    """)

    fun `test function tail expr is macro 1`() = doTest("""
        fn main() {
            foo()
        }

        fn /*caret*/foo() {
            bar!()
        }
    """, """
        fn main() {
            bar!()
        }
    """)

    fun `test function tail expr is macro 2`() = doTest("""
        fn main() {
            foo()
        }

        fn /*caret*/foo() {
            bar();
            bar!()
        }
    """, """
        fn main() {
            bar();
            bar!()
        }
    """)

    fun `test function tail expr is macro with curly brackets`() = doTest("""
        fn main() {
            let _ = foo();
        }

        fn /*caret*/foo() -> TokenStream {
            bar();
            quote! {}
        }
    """, """
        fn main() {
            bar();
            let _ = quote! {};
        }
    """)

    fun `test function first statement is macro`() = doTest("""
        fn main() {
            foo(arg());
        }

        fn /*caret*/foo(p: i32) {
            bar!();
            bar(p);
        }
    """, """
        fn main() {
            let p = arg();
            bar!();
            bar(p);
        }
    """)

    fun `test function first statement is item`() = doTest("""
        fn main() {
            foo(arg());
        }

        fn /*caret*/foo(p: i32) {
            fn inner() {}
            bar(p);
        }
    """, """
        fn main() {
            let p = arg();
            fn inner() {}
            bar(p);
        }
    """)

    fun `test parameter is Fn trait`() = doTest("""
        fn main() {
            let func = || 1;
            foo(&func);
        }

        fn /*caret*/foo(func: &dyn Fn() -> i32) {
            func();
        }
    """, """
        fn main() {
            let func = || 1;
            func();
        }
    """)

    fun `test parameter used as field shorthand in struct literal (argument is literal)`() = doTest("""
        struct Foo { field: i32 }
        fn main() {
            foo(1);
        }

        fn /*caret*/foo(field: i32) {
            let _ = Foo { field };
        }
    """, """
        struct Foo { field: i32 }
        fn main() {
            let _ = Foo { field: 1 };
        }
    """)

    fun `test parameter used as field shorthand in struct literal (argument is variable)`() = doTest("""
        struct Foo { field: i32 }
        fn main() {
            let a = 1;
            foo(a);
        }

        fn /*caret*/foo(field: i32) {
            let _ = Foo { field };
        }
    """, """
        struct Foo { field: i32 }
        fn main() {
            let a = 1;
            let _ = Foo { field: a };
        }
    """)

    fun `test parameter used as field shorthand in struct literal (argument is variable same name)`() = doTest("""
        struct Foo { field: i32 }
        fn main() {
            let field = 1;
            foo(field);
        }

        fn /*caret*/foo(field: i32) {
            let _ = Foo { field };
        }
    """, """
        struct Foo { field: i32 }
        fn main() {
            let field = 1;
            let _ = Foo { field };
        }
    """)

    fun `test parameter used in macro`() = doTest("""
        macro_rules! gen {
            ($ e:expr) => { bar($ e) };
        }

        fn main() {
            let a = 1;
            foo(a);
        }

        fn /*caret*/foo(p: i32) {
            gen!(p);
        }
    """, """
        macro_rules! gen {
            ($ e:expr) => { bar($ e) };
        }

        fn main() {
            let a = 1;
            gen!(a);
        }
    """)

    fun `test method call used as function argument`() = doTest("""
        struct Foo { field: i32 }
        impl Foo {
            fn main(&self) {
                bar(self.get_field());
            }
            fn /*caret*/get_field(&self) -> i32 { self.field }
        }
    """, """
        struct Foo { field: i32 }
        impl Foo {
            fn main(&self) {
                bar(self.field);
            }
        }
    """)

    fun `test method called on a field`() = doTest("""
        fn usage(foo: &Foo) {
            foo.bar.get_field();
        }

        struct Foo { bar: Bar }
        struct Bar { field: i32 }
        impl Bar {
            fn /*caret*/get_field(&self) -> i32 {
                self.field
            }
        }
    """, """
        fn usage(foo: &Foo) {
            let self1 = &foo.bar;
            self1.field;
        }

        struct Foo { bar: Bar }
        struct Bar { field: i32 }
        impl Bar {}
    """)

    fun `test replace Self if needed`() = doTest("""
        fn func() {
            Foo::new();
        }

        struct Foo {}
        impl Foo {
            fn /*caret*/new() -> Self {
                Self {}
            }
            fn new2() -> Self {
                Self::new()
            }
        }

        trait Trait {
            fn new3() -> Self;
        }
        impl Trait for Foo {
            fn new3() -> Self {
                Self::new()
            }
        }
    """, """
        fn func() {
            Foo {};
        }

        struct Foo {}
        impl Foo {
            fn new2() -> Self {
                Self {}
            }
        }

        trait Trait {
            fn new3() -> Self;
        }
        impl Trait for Foo {
            fn new3() -> Self {
                Self {}
            }
        }
    """)

    fun `test replace Self in method with generics`() = doTest("""
        fn func(foo: Foo<i32>) {
            foo.copy();
        }

        struct Foo<T> { t: T }
        impl<T> Foo<T> {
            fn /*caret*/copy(&self) -> Self {
                let _: Self;
                Self::new();
                Self { t: self.t }
            }
        }
    """, """
        fn func(foo: Foo<i32>) {
            let _: Foo<i32>;
            Foo::<i32>::new();
            Foo::<i32> { t: foo.t };
        }

        struct Foo<T> { t: T }
        impl<T> Foo<T> {}
    """)

    fun `test replace Self in method with generics UFCS`() = doTest("""
        fn func(x: i32) {
            let _ = Foo::new(x);
        }

        struct Foo<T> { t: T }
        impl<T> Foo<T> {
            fn /*caret*/new(t: T) -> Self {
                Self { t }
            }
        }
    """, """
        fn func(x: i32) {
            let _ = Foo::<i32> { t: x };
        }

        struct Foo<T> { t: T }
        impl<T> Foo<T> {}
    """)

    fun `test nested function call`() = doTest("""
        fn main() {
            let _ = foo(foo(2));
        }

        fn /*caret*/foo(x: usize) -> usize { x + 1 }
    """, """
        fn main() {
            let _ = (2 + 1) + 1;
        }
    """)
}
