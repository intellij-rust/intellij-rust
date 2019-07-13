/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.psi.PsiElement
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.findCargoProject
import org.rust.openapiext.toPsiDirectory

class TestRunConfigurationProducerTest : RunConfigurationProducerTestBase() {
    fun `test test producer works for annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer uses complete function path`() {
        testProject {
            lib("foo", "src/lib.rs", """
            mod foo_mod {
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer disabled for non annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", "fn test_foo() { /*caret*/assert!(true); }").open()
        }
        checkOnLeaf()
    }

    fun `test test producer remembers context`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() {
                    assert_eq!(2 + 2, 4);
                }

                #[test]
                fn test_bar() {
                    assert_eq!(2 * 2, 4);
                }
            """).open()
        }

        val ctx1 = myFixture.findElementByText("+", PsiElement::class.java)
        val ctx2 = myFixture.findElementByText("*", PsiElement::class.java)
        doTestRemembersContext(CargoTestRunConfigurationProducer(), ctx1, ctx2)
    }

    fun `test test producer remembers context in test mod`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[cfg(test)]
                mod tests {
                    fn foo() {
                        let x = 2 + 2;
                    }

                    #[test]
                    fn test_bar() {
                        let x = 2 * 2;
                    }
                }
            """).open()
        }

        val ctx1 = myFixture.findElementByText("+", PsiElement::class.java)
        val ctx2 = myFixture.findElementByText("*", PsiElement::class.java)
        doTestRemembersContext(CargoTestRunConfigurationProducer(), ctx1, ctx2)
    }

    fun `test test producer works for modules`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    #[test] fn bar() {}

                    #[test] fn baz() {}

                    fn quux() {/*caret*/}
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test test producer works for module declarations`() {
        testProject {
            file("src/tests.rs", """
                #[test]
                fn test() {}
            """)
            lib("foo", "src/lib.rs", """
                mod tests/*caret*/;
            """).open()
        }
        checkOnTopLevel<RsModDeclItem>()
    }

    fun `test test producer works for nested modules 1`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    mod bar {
                        #[test] fn bar() {}

                        #[test] fn baz() {}

                        fn quux() { /*caret*/ }
                    }
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test test producer works for nested modules 2`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    mod bar {
                        #[test] fn bar() {}

                        #[test] fn baz() {}
                    }
                    fn quux() { /*caret*/ }
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test test producer works for files`() {
        testProject {
            test("foo", "tests/foo.rs").open()
        }
        checkOnElement<RsFile>()
    }

    fun `test test producer works for root module`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test] fn bar() {}

                #[test] fn baz() {}

                fn quux() {/*caret*/}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test meaningful test configuration name`() {
        testProject {
            lib("foo", "src/lib.rs", "mod bar;")
            file("src/bar/mod.rs", """
                mod tests {
                    fn quux() /*caret*/{}

                    #[test] fn baz() {}
                }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test take into account path attribute`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[cfg(test)]
                #[path = "foo.rs"]
                mod test;
            """)
            file("src/foo.rs", """
                #[test]
                fn foo() {/*caret*/}
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer adds bin name`() {
        testProject {
            bin("foo", "src/bin/foo.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test test configuration uses default environment`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }

        modifyTemplateConfiguration {
            channel = RustChannel.NIGHTLY
            allFeatures = true
            nocapture = true
            emulateTerminal = true
            backtrace = BacktraceMode.FULL
            env = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
        }

        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer works for multiple files`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)

            test("bar", "tests/bar.rs", """
                #[test] fn test_bar() {}
            """)

            test("baz", "tests/baz.rs", """
                #[test] fn test_baz() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("tests/baz.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
    }

    fun `test test producer ignores selected files that contain no tests`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)

            test("bar", "tests/bar.rs", """
                fn test_bar() {}
            """)

            test("baz", "tests/baz.rs", """
                #[test] fn test_baz() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("tests/bar.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
    }

    fun `test test producer works for tests source root`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val sourceRoot = myFixture.file.containingDirectory
        checkOnFiles(sourceRoot)
    }

    fun `test test producer works for directories inside tests source root`() {
        testProject {
            test("foo", "tests/dir/foo.rs", """
                #[test] fn test_foo() {}
            """)

            test("bar", "tests/dir/bar.rs", """
                fn test_bar() {}
            """)

            test("baz", "tests/dir/baz.rs", """
                #[test] fn test_baz() {}
            """)
        }

        openFileInEditor("tests/dir/foo.rs")
        val dir = myFixture.file.containingDirectory
        checkOnFiles(dir)
    }

    fun `test test producer doesn't works for directories without tests`() {
        testProject {
            test("foo", "tests/foo.rs", """
                fn foo() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val dir = myFixture.file.containingDirectory
        checkOnFiles(dir)
    }

    fun `test test producer works for project root`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val packageRoot = myFixture.file
            .containingDirectory
            .findCargoProject()
            ?.rootDir
            ?.toPsiDirectory(project)!!
        checkOnFiles(packageRoot)
    }
}
