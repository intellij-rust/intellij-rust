/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.rust.RsTestBase

class RsInlineFunctionTest : RsTestBase() {
    fun `test inline function without parameters and a return value`() = doTest("""
        fn main() {
            test();
        }
        fn /*caret*/test() {
            println!("test");
            println!("test2");
        }
    """, """
        fn main() {
            println!("test");
            println!("test2");
        }

    """)

    fun `test inline function with parameter and no return value`() = doTest("""
        fn main() {
            let bar = 10i32;
            foo(bar);
        }
        fn /*caret*/foo(bar: i32) {
            println!("{}", bar);
        }
    """, """
        fn main() {
            let bar = 10i32;
            println!("{}", bar);
        }

    """)

    fun `test inline function with return value and no parameters`() = doTest("""
        fn main() {
            let baz = foo();
        }
        fn /*caret*/foo() -> i32 {
            return 10i32;
        }
    """, """
        fn main() {
            let baz = 10i32;
        }

    """)

    fun `test inline function with return value and no parameters, with value assigned inside function`() = doTest("""
        fn main() {
            let baz = foo();
        }
        fn /*caret*/foo() -> i32 {
            let bar = 10i32;
            return bar;
        }
    """, """
        fn main() {
            let bar = 10i32;
            let baz = bar;
        }

    """)

    fun `test inline function with return value and no parameters, with range operator`() = doTest("""
        fn main() {
            for i in 0..foo() {}
        }
        fn /*caret*/foo() -> i32 { 42 }
    """, """
        fn main() {
            for i in 0..42 {}
        }

    """)

    fun `test inline function with return value and no parameters, with range operator and assignment in function`() = doTest("""
        fn main() {
            for i in 0..foo() {}
        }
        fn /*caret*/foo() -> i32 {
            let bar = 10i32;
            return bar;
        }
    """, """
        fn main() {
            let bar = 10i32;
            for i in 0..bar {}
        }

    """)

    fun `test inline function with return value and no parameters, inside boolean expression`() = doTest("""
        fn main() {
            if foo() > 5 {}
        }
        fn /*caret*/foo() -> i32 { 42 }
    """, """
        fn main() {
            if 42 > 5 {}
        }

    """)

    fun `test inline input parameter with mutability`() = doTest("""
        fn main() {
            let mut vec = vec![1, 2, 3];
            foo(&mut vec);
        }
        fn /*caret*/foo(vec: &mut Vec<i32>) {
            vec.push(1);
        }
    """, """
        fn main() {
            let mut vec = vec![1, 2, 3];
            vec.push(1);
        }

    """)

    fun `test inline function with 2 parameters and no return value`() = doTest("""
        fn main() {
            let bar = 10i32;
            let baz = 20i32;
            foo(bar, baz);
        }
        fn /*caret*/foo(bar: i32, baz: i32) {
            println!("{}, {}", bar, baz);
        }
    """, """
        fn main() {
            let bar = 10i32;
            let baz = 20i32;
            println!("{}, {}", bar, baz);
        }

    """)

    fun `test inline function with tuple return value`() = doTest("""
        fn main() {
            let (barCopy, bazCopy) = foo();
            println!("{}", bar);
            println!("{}", baz);
        }
        fn /*caret*/foo() -> (i32, i32) {
            let bar = 10i32;
            let baz = 20i32;
            (bar, baz)
        }
    """, """
        fn main() {
            let bar = 10i32;
            let baz = 20i32;
            let (barCopy, bazCopy) = (bar, baz);
            println!("{}", bar);
            println!("{}", baz);
        }

    """)

    fun `test inline function with expression return value`() = doTest("""
        fn bar() -> (i32, i32) {
            let baz = 20i32;
            foo(baz);
        }
        fn /*caret*/foo(baz: i32) -> (i32, i32) {
            let qux = 10i32;
            (baz, qux)
        }
    """, """
        fn bar() -> (i32, i32) {
            let baz = 20i32;
            let qux = 10i32;
            (baz, qux)
        }

    """)

    fun `test inline public function`() = doTest("""
        fn main() {
            foo();
        }
        pub fn /*caret*/foo() {
            println!("test");
            println!("test2");
        }
    """, """
        fn main() {
            println!("test");
            println!("test2");
        }

    """)

    fun `test inline method`() = doTest("""
        struct S;
        impl S {
            fn foo() {
                S::bar();
            }

            fn /*caret*/bar() {
                println!("test");
                println!("test2");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo() {
                println!("test");
                println!("test2");
            }
        }
    """)

    fun `test inline method with generics`() = doTest("""
        struct S<T>(T);
        impl<T> S<T> {
            fn foo() {
                <S<T>>::bar();
            }

            fn /*caret*/bar() {
                println!("Hello!");
            }
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn foo() {
                println!("Hello!");
            }
        }
    """)

    fun `test inline method with self parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(self) {
                self.bar();
            }

            fn /*caret*/bar(self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(self) {
                println!("bla");
            }
        }
    """)

    fun `test inline method with ref self parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(&self) {
                self.bar();
            }

            fn /*caret*/bar(&self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&self) {
                println!("bla");
            }
        }
    """)

    fun `test inline method with ref mut self parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(&mut self) {
                self.bar();
            }

            fn /*caret*/bar(&mut self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&mut self) {
                println!("bla");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self) {
                println!("test");
                println!("test2");
                self.test();
            }

            fn test(&mut self) {
                println!("bla");
            }
        }
    """)

    fun `test inline method with self parameter and another parameter`() = doTest("""
        struct S;
        impl S {
            fn foo(self) {
                let test = 10i32;
                self.bar(test);
            }

            fn /*caret*/bar(self, test: i32) {
                println!("{}", test);
                self.test();
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
                println!("{}", test);
                self.test();
            }

            fn test(self) {
                println!("bla");
            }
        }
    """)

    fun `test inline method with public visibility`() = doTest("""
        struct S;
        impl S {
            fn foo() {
                S::bar();
            }

            pub fn /*caret*/bar() {
                println!("test");
                println!("test2");
            }
        }
    """, """
        struct S;
        impl S {
            fn foo() {
                println!("test");
                println!("test2");
            }
        }
    """)

    fun `test inline trait method`() = doTest("""
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
            fn /*caret*/bar() {
                println!("test");
                println!("test2");
            }
        }
    """, """
        struct S;

        trait Bar {
            fn foo();
        }

        impl Bar for S {
            fn foo() {
                println!("test");
                println!("test2");
            }
        }

        impl S {}
    """)

    fun `test inline method in a trait`() = doTest("""
        trait Foo {
            fn foo(&self) {
                let b = 1;
                Self::bar(b);
            }

            fn /*caret*/bar(b: i32) {
                println!("{}", b);
            }
        }
    """, """
        trait Foo {
            fn foo(&self) {
                let b = 1;
                println!("{}", b);
            }
        }
    """)

    fun `test inline function with generic parameters`() = doTest("""
        fn foo<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) {
            bar(a, b, c, d)
        }
        fn /*caret*/bar<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) -> () {
            a;
            b;
            c;
            d;
            println!("test")
        }
    """, """
        fn foo<A, B, C, D>(a: A, b: B, c: Option<C>, d: Option<D>) {
            a;
            b;
            c;
            d;
            println!("test")
        }

    """)

    fun `test inline function with generic parameters and return value`() = doTest("""
        fn foo<T: Default>() -> T {
            bar()
        }
        fn /*caret*/bar<T: Default>() -> T {
            T::default()
        }
    """, """
        fn foo<T: Default>() -> T {
            T::default()
        }

    """)

    fun `test inline function with generic parameters and return generic option value`() = doTest("""
        fn foo<T: Default>() -> Option<T> {
            bar()
        }
        fn /*caret*/bar<T: Default>() -> Option<T> {
            Some(T::default())
        }
    """, """
        fn foo<T: Default>() -> Option<T> {
            Some(T::default())
        }

    """)

    fun `test inline function with generic parameters and where clauses`() = doTest("""
        trait Trait1 {}
        trait Trait2 {}
        trait Trait3 {}
        fn foo<T, U>(t: T, u: U) where T: Trait1 + Trait2, U: Trait3 {
            bar(t, u)
        }
        fn /*caret*/bar<T, U>(t: T, u: U) -> () where T: Trait1 + Trait2, U: Trait3 {
            t;
            u;
            println!("test")
        }
    """, """
        trait Trait1 {}
        trait Trait2 {}
        trait Trait3 {}
        fn foo<T, U>(t: T, u: U) where T: Trait1 + Trait2, U: Trait3 {
            t;
            u;
            println!("test")
        }

    """)

    fun `test inline function with bounded generic parameters`() = doTest("""
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            bar(b);
        }
        fn /*caret*/bar<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            b;
        }
    """, """
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T, F: Foo<T>, B: Bar<Baz<F>>>(b: B) {
            b;
        }

    """)

    fun `test inline function with bounded generic parameters and where clauses`() = doTest("""
        trait T1 {}
        trait T2 {}
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T: T1, U, F: Foo<T>, B>(b: B, u: U) where T: T2, B: Bar<F> + Baz<F> {
            bar(b);
            u;
        }
        fn /*caret*/bar<T: T1, F: Foo<T>, B>(b: B) where T: T2, B: Bar<F> + Baz<F> {
            b;
        }
    """, """
        trait T1 {}
        trait T2 {}
        trait Foo<T> {}
        trait Bar<T> {}
        trait Baz<T> {}
        fn foo<T: T1, U, F: Foo<T>, B>(b: B, u: U) where T: T2, B: Bar<F> + Baz<F> {
            b;
            u;
        }

    """)

    fun `test inline function with passing primitive`() = doTest("""
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
        fn /*caret*/bar(i: i32, f: f64, b: bool, c: char) {
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

            println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);

            println!("{}", i);
            println!("{}", f);
            println!("{}", b);
            println!("{}", c);
        }

    """)

    fun `test inline function with passing reference`() = doTest("""
        fn foo() {
            let s = "str";
            bar(s);
            println!("{}", s);
        }
        fn /*caret*/bar(s: &str) {
            println!("{}", s);
        }
    """, """
        fn foo() {
            let s = "str";
            println!("{}", s);
            println!("{}", s);
        }

    """)

    fun `test inline function with passing copy trait`() = doTest("""
        #[derive(Copy, Clone, Debug)]
        struct Copyable;

        fn foo() {
            let copy = Copyable;
            bar(copy);
            println!("{:?}", copy);
        }
        fn /*caret*/bar(copy: Copyable) {
            println!("{:?}", copy);
        }
    """, """
        #[derive(Copy, Clone, Debug)]
        struct Copyable;

        fn foo() {
            let copy = Copyable;
            println!("{:?}", copy);
            println!("{:?}", copy);
        }

    """)

    fun `test inline function with passing by &`() = doTest("""
        fn f(a: &Vec<i32>) {}

        fn foo() {
            let vec = vec![1, 2, 3];
            let vec2 = vec![1, 2, 3];
            let vec3 = vec![1, 2, 3];

            bar(&vec, vec2, &vec3);

            println!("{}", vec.len());
        }
        fn /*caret*/bar(vec: &Vec<i32>, vec2: Vec<i32>, vec3: &Vec<i32>) {
            println!("{}", vec.len());
            println!("{}", vec2.len());
            f(&vec3);
        }
    """, """
        fn f(a: &Vec<i32>) {}

        fn foo() {
            let vec = vec![1, 2, 3];
            let vec2 = vec![1, 2, 3];
            let vec3 = vec![1, 2, 3];

            println!("{}", vec.len());
            println!("{}", vec2.len());
            f(&vec3);

            println!("{}", vec.len());
        }

    """)

    fun `test inline function with passing by &mut`() = doTest("""
        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            bar(&mut vec, &mut vec2);

            println!("{}", vec.len());
        }
        fn /*caret*/bar(vec: &mut Vec<i32>, vec2: &mut Vec<i32>) {
            vec.push(123);
            vec2.push(123);
        }
    """, """
        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            vec.push(123);
            vec2.push(123);

            println!("{}", vec.len());
        }

    """)

    fun `test inline function with passing by mut`() = doTest("""
        fn test(mut v: Vec<i32>) {}
        fn test2(v: &mut Vec<i32>) {}

        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            bar(vec, &mut vec2);
        }
        fn /*caret*/bar(mut vec: Vec<i32>, mut vec2: &mut Vec<i32>) {
            test(vec);
            test2(&mut vec2);
        }
    """, """
        fn test(mut v: Vec<i32>) {}
        fn test2(v: &mut Vec<i32>) {}

        fn foo() {
            let mut vec = vec![1, 2, 3];
            let mut vec2 = vec![1, 2, 3];

            test(vec);
            test2(&mut vec2);
        }

    """)

    fun `test extract a complex function as example`() = doTest("""
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
        fn /*caret*/foo(call: _) -> (usize, _, _) {
            let scope = call.scope;
            let test = call.arguments.require(scope, 0)?.check::<JsInteger>()?.value() as usize;
            let callback = call.arguments.require(scope, 1)?.check::<JsFunction>()?;
            let file = FILE.lock().unwrap();
            let file = get_file_or_return_null!(file).clone();
            (test, callback, file)
        }
    """, """
        fn parse_test(call: Call) -> JsResult<JsValue> {
            let scope = call.scope;
            let test = call.arguments.require(scope, 0)?.check::<JsInteger>()?.value() as usize;
            let callback = call.arguments.require(scope, 1)?.check::<JsFunction>()?;
            let file = FILE.lock().unwrap();
            let file = get_file_or_return_null!(file).clone();
            let (test, callback, file) = (test, callback, file);

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

    """)

    private fun doTest(@Language("Rust") before: String,
                       @Language("Rust") after: String) {
        checkByText(before.trimIndent(), after.trimIndent()) {
            myFixture.performEditorAction("Inline")
        }
    }
}
