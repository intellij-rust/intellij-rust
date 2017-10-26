/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.refactoring.extractFunction.RsExtractFunctionConfig
import org.rust.lang.refactoring.extractFunction.RsExtractFunctionHandlerAction


class RsExtractFunctionTest : RsTestBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/extract_function/"

    fun `test basic extraction test`() = doTest("""
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

    fun `test complex extract`() = doTest("""
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
            let (test, callback, file) = foo();

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

        fn foo() -> (usize, _, _) {
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

    fun `test extract basic return type`() = doTest("""
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
                test2()
            }

            fn test2() -> (i32, i32) {
                let test = 10i32;
                (test2, test)
            }
        """,
        false,
        "test2")

    fun `test pub extraction`() = doTest("""
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

    fun `test basic impl extraction`() = doTest("""
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

    fun `test self impl extraction`() = doTest("""
            struct S;
            impl S {
                fn foo(self) {
                    <selection>println!("test");
                    println!("test2");</selection>
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
                }
            }
        """,
        false,
        "bar")

    fun `test impl public extraction`() = doTest("""
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

    fun `test trait extraction`() = doTest("""
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

    private fun doTest(@Language("Rust") code: String, @Language("Rust") excepted: String, pub: Boolean, name: String) {
        checkByText(code, excepted) {
            val start = myFixture.editor?.selectionModel?.selectionStart ?: fail("No start selection")
            val end = myFixture.editor?.selectionModel?.selectionEnd ?: fail("No end selection")

            val config = RsExtractFunctionConfig.create(myFixture.file, start, end)!!
            config.name = name
            config.visibilityLevelPublic = pub

            RsExtractFunctionHandlerAction(
                project,
                myFixture.file,
                config
            ).execute()
        }
    }

    private fun fail(message: String): Nothing {
        TestCase.fail(message)
        error("Test failed with message: \"$message\"")
    }
}
