/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
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
