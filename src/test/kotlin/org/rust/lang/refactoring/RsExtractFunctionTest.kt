/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.refactoring.extractFunction.ExtractFunctionUi
import org.rust.lang.refactoring.extractFunction.RsExtractFunctionConfig
import org.rust.lang.refactoring.extractFunction.withMockExtractFunctionUi


class RsExtractFunctionTest : RsTestBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/extract_function/"

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
        """,
        false,
        "test")

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
        """,
        false,
        "foo")

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
        """,
        false,
        "foo")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "foo")

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
        """,
        false,
        "foo")

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
        """,
        false,
        "foo")

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
        """,
        false,
        "test")

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
        """,
        false,
        "test")

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
        """,
        false,
        "test")

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
        """,
        false,
        "test2")

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
        """,
        true,
        "test")

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
                    S::bar();
                }

                fn bar() {
                    println!("test");
                    println!("test2");
                }
            }
        """,
        false,
        "bar")

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
                    <S<T>>::bar();
                }

                fn bar() {
                    println!("Hello!");
                }
            }
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
                    S::bar(test);
                }

                fn bar(test: i32) {
                    println!("test {}", test);
                    println!("test2");
                }
            }
        """,
        false,
        "bar")

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
                    S::bar();
                }

                pub fn bar() {
                    println!("test");
                    println!("test2");
                }
            }
        """,
        true,
        "bar")

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
                    S::bar();
                }
            }

            impl S {
                fn bar() {
                    println!("test");
                    println!("test2");
                }
            }
""",
        false,
        "bar")

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
        """,
        false,
        "bar")

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
                bar(a, b, c, d)
            }

            fn bar<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) -> () {
                a;
                b;
                c;
                d;
                println!("test")
            }
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
                bar(t, u)
            }

            fn bar<T, U>(t: T, u: U) -> () where T: Trait1 + Trait2, U: Trait3 {
                t;
                u;
                println!("test")
            }
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

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
        """,
        false,
        "bar")

    private fun doTest(@Language("Rust") code: String,
                       @Language("Rust") excepted: String,
                       pub: Boolean,
                       name: String) {
        withMockExtractFunctionUi(object : ExtractFunctionUi {
            override fun extract(config: RsExtractFunctionConfig, callback: () -> Unit) {
                config.name = name
                config.visibilityLevelPublic = pub
                callback()
            }
        }) {
            checkByText(code, excepted) {
                myFixture.performEditorAction("ExtractMethod")
            }
        }
    }
}
