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
