/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.refactoring.extractFunction.ExtractFunctionUi
import org.rust.ide.refactoring.extractFunction.RsExtractFunctionConfig
import org.rust.ide.refactoring.extractFunction.withMockExtractFunctionUi

class RsExtractFunctionTest : RsTestBase() {

    fun `test simple`() = doTest("""
        fn main() {
            /*selection*/println!();/*selection**/
        }
    """, """
        fn main() {
            test();
        }

        fn test() {
            println!();
        }
    """, "test")

    fun `test extract a function without parameters and a return value`() = doTest("""
        fn main() {
            <selection>println!("test");
            println!("test2");</selection>
        }
    """, """
        fn main() {
            test();
        }

        fn test() {
            println!("test");
            println!("test2");
        }
    """, "test")

    fun `test extract a complex function as example`() = doTest("""
        fn parse_test(call: Call) -> JsResult<JsValue> {
            <selection>let scope = call.scope;
            let test = call.arguments.require(scope, 0)?.check::<JsInteger>()?.value() as usize;
            let callback = call.arguments.require(scope, 1)?.check::<JsFunction>()?;
            let file = FILE.lock().unwrap();
            let file = get_file_or_return_null!(file).clone();</selection>

            struct RenderTask(Arc<File>, usize);
            impl Task for RenderTask {
                type Output = String;
                type Error = ();
                type JsEvent = JsString;

                fn perform(&self) -> Result<String, ()> {
                    let mut renderer = renderer();
                    let tree = renderer.render_one(&self.0, self.1);
                    Ok(tree)
                }

                fn complete<'a, T: Scope<'a>>(self, scope: &'a mut T, result: Result<String, ()>) -> JsResult<JsString> {
                    Ok(JsString::new(scope, &result.unwrap()).unwrap())
                }
            }

            RenderTask(file, test).schedule(callback);
            Ok(JsNull::new().upcast())
        }
    """, """
        fn parse_test(call: Call) -> JsResult<JsValue> {
            let (test, callback, file) = foo(call);

            struct RenderTask(Arc<File>, usize);
            impl Task for RenderTask {
                type Output = String;
                type Error = ();
                type JsEvent = JsString;

                fn perform(&self) -> Result<String, ()> {
                    let mut renderer = renderer();
                    let tree = renderer.render_one(&self.0, self.1);
                    Ok(tree)
                }

                fn complete<'a, T: Scope<'a>>(self, scope: &'a mut T, result: Result<String, ()>) -> JsResult<JsString> {
                    Ok(JsString::new(scope, &result.unwrap()).unwrap())
                }
            }

            RenderTask(file, test).schedule(callback);
            Ok(JsNull::new().upcast())
        }

        fn foo(call: _) -> (usize, _, _) {
            let scope = call.scope;
            let test = call.arguments.require(scope, 0)?.check::<JsInteger>()?.value() as usize;
            let callback = call.arguments.require(scope, 1)?.check::<JsFunction>()?;
            let file = FILE.lock().unwrap();
            let file = get_file_or_return_null!(file).clone();
            (test, callback, file)
        }
    """, "foo")

    fun `test extract basic input parameter`() = doTest("""
        fn main() {
            let bar = 10i32;
            <selection>println!("{}", bar);</selection>
        }
    """, """
        fn main() {
            let bar = 10i32;
            foo(bar);
        }

        fn foo(bar: i32) {
            println!("{}", bar);
        }
    """, "foo")

    fun `test extract input parameter from method`() = doTest("""
        fn foo(a: i32) {
            <selection>println!("{}", a);</selection>
        }
    """, """
        fn foo(a: i32) {
            bar(a);
        }

        fn bar(a: i32) {
            println!("{}", a);
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract input parameter with mutability`() = doTest("""
        fn main() {
            let mut vec = vec![1, 2, 3];
            <selection>vec.push(1);</selection>
        }
    """, """
        fn main() {
            let mut vec = vec![1, 2, 3];
            foo(&mut vec);
        }

        fn foo(vec: &mut Vec<i32>) {
            vec.push(1);
        }
    """, "foo")

    fun `test extract two input parameter`() = doTest("""
        fn main() {
            let bar = 10i32;
            let test = 10i32;
            <selection>println!("{} {}", bar, test);</selection>
        }
    """, """
        fn main() {
            let bar = 10i32;
            let test = 10i32;
            foo(bar, test);
        }

        fn foo(bar: i32, test: i32) {
            println!("{} {}", bar, test);
        }
    """, "foo")

    fun `test extract ignore unused bindings`() = doTest("""
        fn main() {
            let bar = 10i32;
            let test = 10i32;
            <selection>println!("{}", test);</selection>
            println!("{}", bar);
        }
    """, """
        fn main() {
            let bar = 10i32;
            let test = 10i32;
            foo(test);
            println!("{}", bar);
        }

        fn foo(test: i32) {
            println!("{}", test);
        }
    """, "foo")

    fun `test extract ignore unused bindings only before`() = doTest("""
        fn main() {
            let a = 1;
            foo(a);
            let v = 2;
            <selection>println!("{:?}", v);</selection>
        }

        fn foo(a: i32) {
            println!("{}", a);
        }
    """, """
        fn main() {
            let a = 1;
            foo(a);
            let v = 2;
            test(v);
        }

        fn test(v: i32) {
            println!("{:?}", v);
        }

        fn foo(a: i32) {
            println!("{}", a);
        }
    """, "test")

    fun `test extract a function with a return value`() = doTest("""
        fn main() {
            <selection>let test = 10i32;</selection>
            println!("{}", test);
        }
    """, """
        fn main() {
            let test = test();
            println!("{}", test);
        }

        fn test() -> i32 {
            let test = 10i32;
            test
        }
    """, "test")

    fun `test extract return tuple`() = doTest("""
        fn main() {
            <selection>let test2 = 10i32;
            let test = 10i32;</selection>
            println!("{}", test);
            println!("{}", test2);
        }
    """, """
        fn main() {
            let (test2, test) = test();
            println!("{}", test);
            println!("{}", test2);
        }

        fn test() -> (i32, i32) {
            let test2 = 10i32;
            let test = 10i32;
            (test2, test)
        }
    """, "test")

    fun `test extract return parameter expr`() = doTest("""
        fn test() -> (i32, i32) {
            let test2 = 10i32;
            <selection>let test = 10i32;
            (test2, test)</selection>
        }
    """, """
        fn test() -> (i32, i32) {
            let test2 = 10i32;
            test2(test2)
        }

        fn test2(test2: i32) -> (i32, i32) {
            let test = 10i32;
            (test2, test)
        }
    """, "test2")

    fun `test extract a function with public visibility`() = doTest("""
        fn main() {
            <selection>println!("test");
            println!("test2");</selection>
        }
    """, """
        fn main() {
            test();
        }

        pub fn test() {
            println!("test");
            println!("test2");
        }
    """, "test", pub = true)

    fun `test extract a function in impl`() = doTest("""
        struct S;
        impl S {
            fn foo() {
                <selection>println!("test");
                println!("test2");</selection>
            }
        }
    """, """
        struct S;
        impl S {
            fn foo() {
                Self::bar();
            }

            fn bar() {
                println!("test");
                println!("test2");
            }
        }
    """, "bar")

    fun `test extract a function in impl for generic types`() = doTest("""
        struct S<T>(T);
        impl<T> S<T> {
            fn foo() {
                <selection>println!("Hello!");</selection>
            }
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn foo() {
                Self::bar();
            }

            fn bar() {
                println!("Hello!");
            }
        }
    """, "bar")

    fun `test extract a function with the parameter self`() = doTest("""
        struct S;
        impl S {
            fn foo(self) {
                <selection>println!("test");
                println!("test2");
                self.test();</selection>
            }

            fn test(self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(self) {
                self.bar();
            }

            fn bar(self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(self) {
                println!("bla");
            }
        }
    """, "bar")

    fun `test extract a function with the parameter ref self`() = doTest("""
        struct S;
        impl S {
            fn foo(&self) {
                <selection>println!("test");
                println!("test2");
                self.test();</selection>
            }

            fn test(&self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {
                self.bar();
            }

            fn bar(&self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&self) {
                println!("bla");
            }
        }
    """, "bar")

    fun `test extract a function with the parameter ref mut self`() = doTest("""
        struct S;
        impl S {
            fn foo(&mut self) {
                <selection>println!("test");
                println!("test2");
                self.test();</selection>
            }

            fn test(&mut self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self) {
                self.bar();
            }

            fn bar(&mut self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&mut self) {
                println!("bla");
            }
        }
    """, "bar")

    fun `test extract a function with the parameter self and other parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(self) {
                let test = 10i32;
                <selection>println!("{}", test);
                self.test();</selection>
            }

            fn test(self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(self) {
                let test = 10i32;
                self.bar(test);
            }

            fn bar(self, test: i32) {
                println!("{}", test);
                self.test();
            }

            fn test(self) {
                println!("bla");
            }
        }
    """, "bar")

    fun `test extract a function without self because it is not used`() = doTest("""
        struct S;
        impl S {
            fn foo(self) {
                let test = 10i32;
                <selection>println!("test {}", test);
                println!("test2");</selection>
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(self) {
                let test = 10i32;
                Self::bar(test);
            }

            fn bar(test: i32) {
                println!("test {}", test);
                println!("test2");
            }
        }
    """, "bar")

    fun `test extract a function in an impl with public visibility`() = doTest("""
        struct S;
        impl S {
            fn foo() {
                <selection>println!("test");
                println!("test2");</selection>
            }
        }
    """, """
        struct S;
        impl S {
            fn foo() {
                Self::bar();
            }

            pub fn bar() {
                println!("test");
                println!("test2");
            }
        }
    """, "bar", pub = true)

    fun `test extract a function in a impl trait`() = doTest("""
        struct S;

        trait Bar {
            fn foo();
        }

        impl Bar for S {
            fn foo() {
                <selection>println!("test");
                println!("test2");</selection>
            }
        }
    """, """
        struct S;

        trait Bar {
            fn foo();
        }

        impl Bar for S {
            fn foo() {
                Self::bar();
            }
        }

        impl S {
            fn bar() {
                println!("test");
                println!("test2");
            }
        }
    """,  "bar")

    fun `test extract a function in a impl trait (choose existing impl)`() = doTest("""
        struct S;

        trait Bar {
            fn foo();
        }

        impl Bar for S {
            fn foo() {
                <selection>println!("test");
                println!("test2");</selection>
            }
        }

        impl S {}
    """, """
        struct S;

        trait Bar {
            fn foo();
        }

        impl Bar for S {
            fn foo() {
                Self::bar();
            }
        }

        impl S {
            fn bar() {
                println!("test");
                println!("test2");
            }
        }
    """,  "bar")

    fun `test extract a function in a impl trait (choose existing impl with same generic parameters)`() = doTest("""
        struct S1;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                <selection>self.foo();</selection>
            }
        }

        impl Foo<S1> {}
    """, """
        struct S1;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                self.bar();
            }
        }

        impl Foo<S1> {
            fn bar(&self) {
                self.foo();
            }
        }
    """,  "bar")

    fun `test extract a function in a impl trait (don't choose existing impl with different generic parameters)`() = doTest("""
        struct S1;
        struct S2;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                <selection>self.foo();</selection>
            }
        }

        impl Foo<S2> {}
    """, """
        struct S1;
        struct S2;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                self.bar();
            }
        }

        impl Foo<S1> {
            fn bar(&self) {
                self.foo();
            }
        }

        impl Foo<S2> {}
    """, "bar")

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test extract a function in a impl trait (don't choose existing cfg-disabled impl)`() = doTest("""
        struct S1;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                <selection>self.foo();</selection>
            }
        }

        #[cfg(not(intellij_rust))]
        impl Foo<S1> {}
    """, """
        struct S1;
        struct Foo<T> { t: T }

        trait Trait {
            fn foo(&self);
        }

        impl Trait for Foo<S1> {
            fn foo(&self) {
                self.bar();
            }
        }

        impl Foo<S1> {
            fn bar(&self) {
                self.foo();
            }
        }

        #[cfg(not(intellij_rust))]
        impl Foo<S1> {}
    """, "bar")

    fun `test extract a function in a trait`() = doTest("""
        trait Foo {
            fn foo(&self) {
                let b = 1;
                <selection>println!("{}", b);</selection>
            }
        }
    """, """
        trait Foo {
            fn foo(&self) {
                let b = 1;
                Self::bar(b);
            }

            fn bar(b: i32) {
                println!("{}", b);
            }
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with generic parameters`() = doTest("""
        fn foo<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) {
            <selection>a;
            b;
            c;
            d;
            println!("test")</selection>
        }
    """, """
        fn foo<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) {
            bar(a, b, c, d);
        }

        fn bar<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) {
            a;
            b;
            c;
            d;
            println!("test")
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with generic parameters and a return generic value`() = doTest("""
        fn foo<T: Default>() -> T {
            <selection>T::default()</selection>
        }
    """, """
        fn foo<T: Default>() -> T {
            bar()
        }

        fn bar<T: Default>() -> T {
            T::default()
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with generic parameters and a return generic option value`() = doTest("""
        fn foo<T: Default>() -> Option<T> {
            <selection>Some(T::default())</selection>
        }
    """, """
        fn foo<T: Default>() -> Option<T> {
            bar()
        }

        fn bar<T: Default>() -> Option<T> {
            Some(T::default())
        }
    """, "bar")

    fun `test extract a function with generic parameters and where clauses`() = doTest("""
        trait Trait1 {}
        trait Trait2 {}
        trait Trait3 {}
        fn foo<T, U>(t: T, u: U) where T: Trait1 + Trait2, U: Trait3 {
            <selection>t;
            u;
            println!("test")</selection>
        }
    """, """
        trait Trait1 {}
        trait Trait2 {}
        trait Trait3 {}
        fn foo<T, U>(t: T, u: U) where T: Trait1 + Trait2, U: Trait3 {
            bar(t, u);
        }

        fn bar<T, U>(t: T, u: U) where T: Trait1 + Trait2, U: Trait3 {
            t;
            u;
            println!("test")
        }
    """, "bar")

    fun `test extract a function with bounded generic parameters`() = doTest("""
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            <selection>b;</selection>
        }
    """, """
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            bar(b);
        }

        fn bar<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            b;
        }
    """, "bar")

    fun `test extract a function with bounded generic parameters and where clauses`() = doTest("""
        trait T1 {}
        trait T2 {}
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T: T1, U, F: Foo<T>, B>(b: B, u: U) where T: T2, B: Bar<F> + Baz<F> {
            <selection>b;</selection>
            u;
        }
    """, """
        trait T1 {}
        trait T2 {}
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T: T1, U, F: Foo<T>, B>(b: B, u: U) where T: T2, B: Bar<F> + Baz<F> {
            bar(b);
            u;
        }

        fn bar<T: T1, F: Foo<T>, B>(b: B) where T: T2, B: Bar<F> + Baz<F> {
            b;
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with passing primitive`() = doTest("""
        fn foo() {
            let i = 1;
            let f = 1.1;
            let b = true;
            let c = 'c';

            <selection>println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);</selection>

            println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);
        }
    """, """
        fn foo() {
            let i = 1;
            let f = 1.1;
            let b = true;
            let c = 'c';

            bar(i, f, b, c);

            println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);
        }

        fn bar(i: i32, f: f64, b: bool, c: char) {
            println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);
        }
    """, "bar")

    fun `test extract a function with passing reference`() = doTest("""
        fn foo() {
            let s = "str";
            <selection>println!("{}", s);</selection>
            println!("{}", s);
        }
    """, """
        fn foo() {
            let s = "str";
            bar(s);
            println!("{}", s);
        }

        fn bar(s: &str) {
            println!("{}", s);
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with passing copy trait`() = doTest("""
        #[derive(Copy, Clone, Debug)]
        struct Copyable;

        fn foo() {
            let copy = Copyable;
            <selection>println!("{:?}", copy);</selection>
            println!("{:?}", copy);
        }
    """, """
        #[derive(Copy, Clone, Debug)]
        struct Copyable;

        fn foo() {
            let copy = Copyable;
            bar(copy);
            println!("{:?}", copy);
        }

        fn bar(copy: Copyable) {
            println!("{:?}", copy);
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with passing by &`() = doTest("""
        fn f(a: &Vec<i32>) {}

        fn foo() {
            let vec = vec![1, 2, 3];
            let vec2 = vec![1, 2, 3];
            let vec3 = vec![1, 2, 3];

            <selection>println!("{}", vec.len());
            println!("{}", vec2.len());
            f(&vec3);</selection>

            println!("{}", vec.len());
        }
    """, """
        fn f(a: &Vec<i32>) {}

        fn foo() {
            let vec = vec![1, 2, 3];
            let vec2 = vec![1, 2, 3];
            let vec3 = vec![1, 2, 3];

            bar(&vec, vec2, &vec3);

            println!("{}", vec.len());
        }

        fn bar(vec: &Vec<i32>, vec2: Vec<i32>, vec3: &Vec<i32>) {
            println!("{}", vec.len());
            println!("{}", vec2.len());
            f(&vec3);
        }
    """,  "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with passing by &mut`() = doTest("""
        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            <selection>vec.push(123);
            vec2.push(123);</selection>

            println!("{}", vec.len());
        }
    """, """
        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            bar(&mut vec, &mut vec2);

            println!("{}", vec.len());
        }

        fn bar(vec: &mut Vec<i32>, vec2: &mut Vec<i32>) {
            vec.push(123);
            vec2.push(123);
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract a function with passing by mut`() = doTest("""
        fn test(mut v: Vec<i32>) {}
        fn test2(v: &mut Vec<i32>) {}

        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            <selection>test(vec);
            test2(&mut vec2);</selection>
        }
    """, """
        fn test(mut v: Vec<i32>) {}
        fn test2(v: &mut Vec<i32>) {}

        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            bar(vec, &mut vec2);
        }

        fn bar(mut vec: Vec<i32>, mut vec2: &mut Vec<i32>) {
            test(vec);
            test2(&mut vec2);
        }
    """, "bar")

    fun `test extract literal expr`() = doTest("""
        fn main() {
            let a = <selection>42</selection>;
        }
    """, """
        fn main() {
            let a = bar();
        }

        fn bar() -> i32 {
            42
        }
    """, "bar")

    fun `test extract block expr`() = doTest("""
        fn main() {
            let a = <selection>{ 42 }</selection>;
        }
    """, """
        fn main() {
            let a = bar();
        }

        fn bar() -> i32 { 42 }
    """, "bar")

    fun `test extract select arguments`() = doTest("""
        use std::sync::Arc;

        struct Test {
            i: i32
        }

        fn main() {
            let r = 1;
            let s = 2;
            let a = Test { i: 12 };
            let b = { 12 };

            <selection>Arc::new(r);
            Arc::new(s);
            Arc::new(a);
            Arc::new(b);</selection>
        }
    """, """
        use std::sync::Arc;

        struct Test {
            i: i32
        }

        fn main() {
            let r = 1;
            let s = 2;
            let a = Test { i: 12 };
            let b = { 12 };

            bar(r, s, b);
        }

        fn bar(r: i32, s: i32, b: i32) {
            let a: Test;

            Arc::new(r);
            Arc::new(s);
            Arc::new(a);
            Arc::new(b);
        }
    """, "bar", noSelected = listOf("a"))

    fun `test extract expr with trailing semicolon`() = doTest("""
        fn main() {
            let a = <selection>42;</selection>
        }
    """, """
        fn main() {
            let a = bar();
        }

        fn bar() -> i32 {
            42
        }
    """, "bar")

    fun `test extract expr with trailing semicolon and whitespace`() = doTest("""
        fn main() {
            let a = <selection>42; </selection> //
        }
    """, """
        fn main() {
            let a = bar();  //
        }

        fn bar() -> i32 {
            42
        }
    """, "bar")

    fun `test extract expr with trailing comma`() = doTest("""
        fn main() {
            let s = S {
                a: <selection>42,</selection>
                b: 0,
            };
        }
    """, """
        fn main() {
            let s = S {
                a: bar(),
                b: 0,
            };
        }

        fn bar() -> i32 {
            42
        }
    """, "bar")

    fun `test extract async function with await`() = doTest("""
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }

        async fn foo() {
            <selection>async { () }.await</selection>
        }
    """, """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }

        async fn foo() {
            bar().await;
        }

        async fn bar() {
            async { () }.await
        }
    """, "bar")

    fun `test extract async function with nested await`() = doTest("""
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }

        struct S;
        struct W(S);
        impl S {
            async fn foo(&self) -> (u32, u32) { (0, 0) }
        }

        async fn foo() -> u32 {
            let w = W(S);
            <selection>w.0.foo().await.0</selection>
        }
    """, """
        #[lang = "core::future::future::Future"]
        trait Future { type Output; }

        struct S;
        struct W(S);
        impl S {
            async fn foo(&self) -> (u32, u32) { (0, 0) }
        }

        async fn foo() -> u32 {
            let w = W(S);
            bar(w).await
        }

        async fn bar(w: W) -> u32 {
            w.0.foo().await.0
        }
    """, "bar")

    fun `test extract sync function with await inside block`() = doTest("""
        async fn foo() {
            <selection>async { async { () }.await };</selection>
        }
    """, """
        async fn foo() {
            bar();
        }

        fn bar() {
            async { async { () }.await };
        }
    """, "bar")

    fun `test extract sync function with await inside closure`() = doTest("""
        #![feature(async_closure)]
        async fn foo() {
            let x = <selection>async || foo().await</selection>;
        }
    """, """
        #![feature(async_closure)]
        async fn foo() {
            let x = bar();
        }

        fn bar() -> fn() -> _ {
            async || foo().await
        }
    """, "bar")

    fun `test extract with empty lines`() = doTest("""
        fn main() {
            <selection>println!("a");


            // comment
            println!("b");</selection>
        }
    """, """
        fn main() {
            foo();
        }

        fn foo() {
            println!("a");


            // comment
            println!("b");
        }
    """, "foo")

    fun `test extract a function with unset default type parameter`() = doTest("""
        struct S<R, T=u32>(R, T);

        fn main() {
            let s: S<u32> = S(1u32, 2u32);
            <selection>s</selection>;
        }
    """, """
        struct S<R, T=u32>(R, T);

        fn main() {
            let s: S<u32> = S(1u32, 2u32);
            foo(s);
        }

        fn foo(s: S<u32>) -> S<u32> {
            s
        }
    """, "foo")

    fun `test extract a function with unset default const parameter`() = doTest("""
        struct S<const N: usize, const M: usize = 1>([i32; M]);

        fn main() {
            let s: S<0> = S::<0>([1, 2, 3]);
            <selection>s</selection>;
        }
    """, """
        struct S<const N: usize, const M: usize = 1>([i32; M]);

        fn main() {
            let s: S<0> = S::<0>([1, 2, 3]);
            foo(s);
        }

        fn foo(s: S<0>) -> S<0> {
            s
        }
    """, "foo")

    fun `test extract a function with set default type parameter`() = doTest("""
        struct S<R, T=u32>(R, T);

        fn main() {
            let s: S<u32, bool> = S(1u32, true);
            <selection>s</selection>;
        }
    """, """
        struct S<R, T=u32>(R, T);

        fn main() {
            let s: S<u32, bool> = S(1u32, true);
            foo(s);
        }

        fn foo(s: S<u32, bool>) -> S<u32, bool> {
            s
        }
    """, "foo")

    fun `test extract a function can't skip default type parameter`() = doTest("""
        struct S<R=u32, T=u32>(R, T);

        fn main() {
            let s: S<u32, i32> = S(1u32, 2i32);
            <selection>s</selection>;
        }
    """, """
        struct S<R=u32, T=u32>(R, T);

        fn main() {
            let s: S<u32, i32> = S(1u32, 2i32);
            foo(s);
        }

        fn foo(s: S<u32, i32>) -> S<u32, i32> {
            s
        }
    """, "foo")

    fun `test extract a function with unset default type parameters and const parameters`() = doTest("""
        #![feature(const_generics)]

        struct S<
            'a,
            'b,
            'c,
            const A: usize,
            R,
            const B: usize,
            T=u32, // it's not allowed
            const C: usize,
            U=u32
        >(&'a [R; A], &'b [T; B], &'c [U; C]);

        fn main() {
            let s = S(&[1u32], &[2i32], &[3u32]);
            <selection>s</selection>;
        }
    """, """
        #![feature(const_generics)]

        struct S<
            'a,
            'b,
            'c,
            const A: usize,
            R,
            const B: usize,
            T=u32, // it's not allowed
            const C: usize,
            U=u32
        >(&'a [R; A], &'b [T; B], &'c [U; C]);

        fn main() {
            let s = S(&[1u32], &[2i32], &[3u32]);
            foo(s);
        }

        fn foo(s: S<1, u32, 1, i32, 1>) -> S<1, u32, 1, i32, 1> {
            s
        }
    """, "foo")

    fun `test import parameter types`() = doTest("""
        use crate::a::foo;

        mod a {
            pub struct A;
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            let s = foo();
            <selection>s;</selection>
        }
    """, """
        use crate::a::{A, foo};

        mod a {
            pub struct A;
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            let s = foo();
            bar(s);
        }

        fn bar(s: A) {
            s;
        }
    """, "bar")

    fun `test import return type`() = doTest("""
        use crate::a::foo;

        mod a {
            pub struct A;
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            <selection>foo()</selection>;
        }
    """, """
        use crate::a::{A, foo};

        mod a {
            pub struct A;
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            bar();
        }

        fn bar() -> A {
            foo()
        }
    """, "bar")

    fun `test do not import default types`() = doTest("""
        use crate::a::foo;

        mod a {
            pub struct S;
            pub struct A<T = S>(T);
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            <selection>foo()</selection>;
        }
    """, """
        use crate::a::{A, foo};

        mod a {
            pub struct S;
            pub struct A<T = S>(T);
            pub fn foo() -> A { unimplemented!() }
        }

        fn main() {
            bar();
        }

        fn bar() -> A {
            foo()
        }
    """, "bar")

    fun `test import non default types`() = doTest("""
        use crate::a::foo;

        mod a {
            pub struct S1;
            pub struct S2;
            pub struct A<T = S1>(T);
            pub fn foo() -> A<S2> { unimplemented!() }
        }

        fn main() {
            <selection>foo()</selection>;
        }
    """, """
        use crate::a::{A, foo, S2};

        mod a {
            pub struct S1;
            pub struct S2;
            pub struct A<T = S1>(T);
            pub fn foo() -> A<S2> { unimplemented!() }
        }

        fn main() {
            bar();
        }

        fn bar() -> A<S2> {
            foo()
        }
    """, "bar")

    fun `test import aliased type`() = doTest("""
        use crate::a::foo;

        mod a {
            pub struct A;
            pub type Foo = A;
            pub fn foo() -> Foo { unimplemented!() }
        }

        fn bar() -> a::Foo {
            let s = foo();
            <selection>s</selection>
        }
    """, """
        use crate::a::{foo, Foo};

        mod a {
            pub struct A;
            pub type Foo = A;
            pub fn foo() -> Foo { unimplemented!() }
        }

        fn bar() -> a::Foo {
            let s = foo();
            bar(s)
        }

        fn bar(s: Foo) -> Foo {
            s
        }
    """, "bar")

    fun `test extract set value to mutable`() = doTest("""
        fn main() {
            let a = 1u32;
            <selection>println!(a);</selection>
        }
    """, """
        fn main() {
            let a = 1u32;
            foo(a);
        }

        fn foo(mut a: u32) {
            println!(a);
        }
    """, "foo", mutabilityOverride = mapOf("a" to true))

    fun `test extract a function set reference to immutable`() = doTest("""
        fn main() {
            let mut a = 1u32;
            <selection>a = 5;</selection>
        }
    """, """
        fn main() {
            let mut a = 1u32;
            foo(&a);
        }

        fn foo(a: &u32) {
            a = 5;
        }
    """, "foo", mutabilityOverride = mapOf("a" to false))

    fun `test extract a function set reference to mutable`() = doTest("""
        fn test(x: &u32) {}

        fn main() {
            let a = 1u32;
            <selection>test(&a);</selection>
        }
    """, """
        fn test(x: &u32) {}

        fn main() {
            let a = 1u32;
            foo(&mut a);
        }

        fn foo(a: &mut u32) {
            test(&a);
        }
    """, "foo", mutabilityOverride = mapOf("a" to true))

    fun `test extract unsafe function`() = doTest("""
        unsafe fn bar(ptr: *const u32) -> u32 {
            <selection>*ptr</selection>
        }
    """, """
        unsafe fn bar(ptr: *const u32) -> u32 {
            foo(ptr)
        }

        unsafe fn foo(ptr: *const u32) -> u32 {
            *ptr
        }
    """, "foo")

    fun `test selection inside a block expression`() = doTest("""
        fn foo() {
            {
                <selection>let a = 1;
                let b = 2;</selection>

                let c = a + b;
            }
        }
    """, """
        fn foo() {
            {
                let (a, b) = bar();

                let c = a + b;
            }
        }

        fn bar() -> (i32, i32) {
            let a = 1;
            let b = 2;
            (a, b)
        }
    """, "bar")

    fun `test selection inside a block statement`() = doTest("""
        fn foo() {
            {
                <selection>let a = 1;
                let b = 2;</selection>
                let c = a + b;
                ()
            }

            ()
        }
    """, """
        fn foo() {
            {
                let (a, b) = bar();
                let c = a + b;
                ()
            }

            ()
        }

        fn bar() -> (i32, i32) {
            let a = 1;
            let b = 2;
            (a, b)
        }
    """, "bar")

    fun `test respect aliases in extracted function`() = doTest("""
        struct S;
        struct R<T>(T);

        type Type1 = S;
        type Type2 = u32;
        type Type3 = R<u32>;

        fn foo() -> Type3 {
            let a: Type1 = S;
            let b: Type3 = R(0);
            let e: Type2 = 0;

            <selection>let c = a;
            let d = b;
            (d, e)</selection>
        }
    """, """
        struct S;
        struct R<T>(T);

        type Type1 = S;
        type Type2 = u32;
        type Type3 = R<u32>;

        fn foo() -> Type3 {
            let a: Type1 = S;
            let b: Type3 = R(0);
            let e: Type2 = 0;

            bar(a, b, e)
        }

        fn bar(a: Type1, b: Type3, e: Type2) -> (Type3, Type2) {
            let c = a;
            let d = b;
            (d, e)
        }
    """, "bar")

    fun `test type with default type argument in extracted function`() = doTest("""
        struct S<T = u32>(T);

        fn foo() -> S<u32> {
            let a = S(0u32);

            <selection>let b = a;
            b</selection>
        }
    """, """
        struct S<T = u32>(T);

        fn foo() -> S<u32> {
            let a = S(0u32);

            bar(a)
        }

        fn bar(a: S) -> S {
            let b = a;
            b
        }
    """, "bar")

    fun `test extract a function in a generic impl trait with multiple type parameters`() = doTest("""
        trait Trait {
            fn fn_bar() {}
        }

        trait Trait2 {
            fn fn_bar() {}
        }

        enum Result<T, E> {
            Ok(T),
            Err(E),
        }

        impl<T, E> Trait for Result<T, E> where (T, E): Trait2 {
            fn fn_bar() {
                <selection>println!("hello");</selection>
            }
        }
    """, """
        trait Trait {
            fn fn_bar() {}
        }

        trait Trait2 {
            fn fn_bar() {}
        }

        enum Result<T, E> {
            Ok(T),
            Err(E),
        }

        impl<T, E> Trait for Result<T, E> where (T, E): Trait2 {
            fn fn_bar() {
                Self::bar();
            }
        }

        impl<T, E> Result<T, E> where (T, E): Trait2 {
            fn bar() {
                println!("hello");
            }
        }
    """, "bar")

    fun `test extract a function in a generic impl trait with where`() = doTest("""
        trait Trait2 {}

        struct Foo<T> where T: Trait2 { t: T }
        trait Trait {
            fn foo(&self);
        }

        impl<T> Trait for Foo<T> where T: Trait2 {
            fn foo(&self) {
                <selection>println!("test");</selection>
            }
        }
    """, """
        trait Trait2 {}

        struct Foo<T> where T: Trait2 { t: T }
        trait Trait {
            fn foo(&self);
        }

        impl<T> Trait for Foo<T> where T: Trait2 {
            fn foo(&self) {
                Self::bar();
            }
        }

        impl<T> Foo<T> where T: Trait2 {
            fn bar() {
                println!("test");
            }
        }
    """, "bar")

    fun `test extract a function in impl trait with lifetimes`() = doTest("""
        struct Foo<'a, T>(&'a T);
        trait Trait {
            fn foo(&self);
        }
        trait Trait2<'a> {}

        impl<'a, T> Trait for Foo<'a, T> where T: Trait2<'a> {
            fn foo(&self) {
                <selection>println!("test");</selection>
            }
        }
    """, """
        struct Foo<'a, T>(&'a T);
        trait Trait {
            fn foo(&self);
        }
        trait Trait2<'a> {}

        impl<'a, T> Trait for Foo<'a, T> where T: Trait2<'a> {
            fn foo(&self) {
                Self::bar();
            }
        }

        impl<'a, T> Foo<'a, T> where T: Trait2<'a> {
            fn bar() {
                println!("test");
            }
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract println! argument expression`() = doTest("""
        fn main() {
            println!("{}", <selection>1 + 2</selection>);
        }
    """, """
        fn main() {
            println!("{}", bar());
        }

        fn bar() -> i32 {
            1 + 2
        }
    """, "bar")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test extract vec! argument expression`() = doTest("""
        fn main() {
            vec![<selection>1 + 2</selection>];
        }
    """, """
        fn main() {
            vec![bar()];
        }

        fn bar() -> i32 {
            1 + 2
        }
    """, "bar")

    fun `test return without value`() = doTest("""
        fn func(f: bool) {
            /*selection*/if f { return; }/*selection**/
        }
    """, """
        fn func(f: bool) {
            if test(f) { return; }
        }

        fn test(f: bool) -> bool {
            if f { return true; }
            false
        }
    """, "test")

    fun `test return with value`() = doTest("""
        fn func(f: bool) -> i32 {
            /*selection*/if f { return 1; }/*selection**/
            2
        }
    """, """
        fn func(f: bool) -> i32 {
            if let Some(value) = test(f) {
                return value;
            }
            2
        }

        fn test(f: bool) -> Option<i32> {
            if f { return Some(1); }
            None
        }
    """, "test")

    fun `test return with value inside lambda`() = doTest("""
        fn func() {
            |f: bool| {
                /*selection*/if f { return 1; }/*selection**/
                2
            };
        }
    """, """
        fn func() {
            |f: bool| {
                if let Some(value) = test(f) {
                    return value;
                }
                2
            };
        }

        fn test(f: bool) -> Option<i32> {
            if f { return Some(1); }
            None
        }
    """, "test")

    fun `test return without value (expr)`() = doTest("""
        fn func(f: bool) {
            let x = /*selection*/if f { return; } else { 'c' }/*selection**/;
        }
    """, """
        fn func(f: bool) {
            let x = match test(f) {
                Some(value) => value,
                None => return,
            };
        }

        fn test(f: bool) -> Option<char> {
            Some(if f { return None; } else { 'c' })
        }
    """, "test")

    fun `test return with value (expr)`() = doTest("""
        fn func(f: bool) -> i32 {
            let x = /*selection*/if f { return 1; } else { 'c' }/*selection**/;
            2
        }
    """, """
        fn func(f: bool) -> i32 {
            let x = match test(f) {
                Ok(value) => value,
                Err(value) => return value,
            };
            2
        }

        fn test(f: bool) -> Result<char, i32> {
            Ok(if f { return Err(1); } else { 'c' })
        }
    """, "test")

    fun `test continue`() = doTest("""
        fn func(f: bool) {
            loop {
                /*selection*/if f { continue; }/*selection**/
            }
        }
    """, """
        fn func(f: bool) {
            loop {
                if test(f) { continue; }
            }
        }

        fn test(f: bool) -> bool {
            if f { return true; }
            false
        }
    """, "test")

    fun `test break`() = doTest("""
        fn func(f: bool) {
            loop {
                /*selection*/if f { break; }/*selection**/
            }
        }
    """, """
        fn func(f: bool) {
            loop {
                if test(f) { break; }
            }
        }

        fn test(f: bool) -> bool {
            if f { return true; }
            false
        }
    """, "test")

    fun `test break with label`() = doTest("""
        fn func(f: bool) {
            'outer: loop {
                loop {
                    /*selection*/if f { break 'outer; }/*selection**/
                }
            }
        }
    """, """
        fn func(f: bool) {
            'outer: loop {
                loop {
                    if test(f) { break 'outer; }
                }
            }
        }

        fn test(f: bool) -> bool {
            if f { return true; }
            false
        }
    """, "test")

    fun `test break with value inside labeled block`() = doTest("""
        fn func(f: bool) {
            let x = 'label: {
                /*selection*/if f { break 'label 1; }/*selection**/
                2
            };
        }
    """, """
        fn func(f: bool) {
            let x = 'label: {
                if let Some(value) = test(f) {
                    break 'label value;
                }
                2
            };
        }

        fn test(f: bool) -> Option<i32> {
            if f { return Some(1); }
            None
        }
    """, "test")

    fun `test break inside nested loop`() = doTest("""
        fn func(f: bool) {
            loop {
                /*selection*/loop { break; }
                if f { continue; }/*selection**/
            }
        }
    """, """
        fn func(f: bool) {
            loop {
                if test(f) { continue; }
            }
        }

        fn test(f: bool) -> bool {
            loop { break; }
            if f { return true; }
            false
        }
    """, "test")

    fun `test return inside nested function`() = doTest("""
        fn func(f: bool) {
            loop {
                /*selection*/if f {
                    fn inner() -> i32 { return 0; }
                    continue;
                }/*selection**/
            }
        }
    """, """
        fn func(f: bool) {
            loop {
                if test(f) { continue; }
            }
        }

        fn test(f: bool) -> bool {
            if f {
                fn inner() -> i32 { return 0; }
                return true;
            }
            false
        }
    """, "test")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test try option`() = doTest("""
        fn foo() -> Option<i32> { None }
        fn func() -> Option<i32> {
            /*selection*/foo()?;/*selection**/
            None
        }
    """, """
        fn foo() -> Option<i32> { None }
        fn func() -> Option<i32> {
            test()?;
            None
        }

        fn test() -> Option<()> {
            foo()?;
            Some(())
        }
    """, "test")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test try option (expr)`() = doTest("""
        fn foo() -> Option<i32> { None }
        fn func() -> Option<bool> {
            let x = /*selection*/foo()?/*selection**/;
            Some(x == 0)
        }
    """, """
        fn foo() -> Option<i32> { None }
        fn func() -> Option<bool> {
            let x = test()?;
            Some(x == 0)
        }

        fn test() -> Option<i32> {
            foo()
        }
    """, "test")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test try result`() = doTest("""
        fn foo() -> Result<i32, &str> { Ok(0) }
        fn func() -> Result<i32, &str> {
            /*selection*/foo()?;/*selection**/
            Ok(0)
        }
    """, """
        fn foo() -> Result<i32, &str> { Ok(0) }
        fn func() -> Result<i32, &str> {
            test()?;
            Ok(0)
        }

        fn test() -> Result<(), &str> {
            foo()?;
            Ok(())
        }
    """, "test")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test try result (expr)`() = doTest("""
        fn foo() -> Result<i32, &str> { Ok(0) }
        fn func() -> Result<bool, &str> {
            let x = /*selection*/foo()?/*selection**/;
            Ok(x == 0)
        }
    """, """
        fn foo() -> Result<i32, &str> { Ok(0) }
        fn func() -> Result<bool, &str> {
            let x = test()?;
            Ok(x == 0)
        }

        fn test() -> Result<i32, &str> {
            foo()
        }
    """, "test")

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test try result (out variables)`() = doTest("""
        fn foo() -> Result<(), &str> { Ok(()) }
        fn func() -> Result<bool, &str> {
            /*selection*/foo()?;
            let x = 0;/*selection**/
            Ok(x == 0)
        }
    """, """
        fn foo() -> Result<(), &str> { Ok(()) }
        fn func() -> Result<bool, &str> {
            let x = test()?;
            Ok(x == 0)
        }

        fn test() -> Result<i32, &str> {
            foo()?;
            let x = 0;
            Ok(x)
        }
    """, "test")

    private fun doTest(
        @Language("Rust") code: String,
        @Language("Rust") excepted: String,
        name: String,
        pub: Boolean = false,
        noSelected: List<String> = emptyList(),
        mutabilityOverride: Map<String, Boolean> = emptyMap()
    ) {
        withMockExtractFunctionUi(object : ExtractFunctionUi {
            override fun extract(config: RsExtractFunctionConfig, callback: () -> Unit) {
                config.name = name
                config.visibilityLevelPublic = pub
                noSelected.forEach { n -> config.parameters.filter { n == it.name }[0].isSelected = false }
                mutabilityOverride.forEach { (key, mutable) ->
                    config.parameters.filter { key == it.name }[0].isMutable = mutable
                }
                callback()
            }
        }) {
            checkEditorAction(replaceSelectionMarker(code), excepted, "ExtractMethod", trimIndent = false)
        }
    }
}
