/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.psi.PsiElement
import org.rust.cargo.runconfig.test.CargoBenchRunConfigurationProducer
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsMod

class BenchRunConfigurationProducerTest : RunConfigurationProducerTestBase() {
    fun `test bench producer works for annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[bench]
                fn bench_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test bench producer uses complete function path`() {
        testProject {
            lib("foo", "src/lib.rs", """
            mod foo_mod {
                #[bench]
                fn bench_foo() { as/*caret*/sert!(true); }
            }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test bench producer disabled for non annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", "fn bench_foo() { /*caret*/assert!(true); }").open()
        }
        checkOnLeaf()
    }

    fun `test bench producer remembers context`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[bench]
                fn bench_foo() {
                    assert_eq!(2 + 2, 4);
                }
                 #[bench]
                fn bench_bar() {
                    assert_eq!(2 * 2, 4);
                }
            """).open()
        }

        val ctx1 = myFixture.findElementByText("+", PsiElement::class.java)
        val ctx2 = myFixture.findElementByText("*", PsiElement::class.java)
        doTestRemembersContext(CargoBenchRunConfigurationProducer(), ctx1, ctx2)
    }

    fun `test bench producer remembers context in test mod`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[cfg(test)]
                mod tests {
                    fn foo() {
                        let x = 2 + 2;
                    }

                    #[bench]
                    fn bench_bar() {
                        let x = 2 * 2;
                    }
                }
            """).open()
        }

        val ctx1 = myFixture.findElementByText("+", PsiElement::class.java)
        val ctx2 = myFixture.findElementByText("*", PsiElement::class.java)
        doTestRemembersContext(CargoBenchRunConfigurationProducer(), ctx1, ctx2)
    }

    fun `test bench producer works for modules`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    #[bench] fn bar() {}

                    #[bench] fn baz() {}

                    fn quux() {/*caret*/}
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test bench producer works for module declarations`() {
        testProject {
            file("src/tests.rs", """
                #[bench]
                fn bench() {}
            """)
            lib("foo", "src/lib.rs", """
                mod tests/*caret*/;
            """).open()
        }
        checkOnTopLevel<RsModDeclItem>()
    }

    fun `test bench producer works for nested modules 1`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    mod bar {
                        #[bench] fn bar() {}

                        #[bench] fn baz() {}

                        fn quux() { /*caret*/ }
                    }
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test bench producer works for nested modules 2`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    mod bar {
                        #[bench] fn bar() {}

                        #[bench] fn baz() {}
                    }
                    fn quux() { /*caret*/ }
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test bench producer works for files`() {
        testProject {
            bench("foo", "benches/foo.rs").open()
        }
        checkOnElement<RsFile>()
    }

    fun `test bench producer works for root module`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[bench] fn bar() {}

                #[bench] fn baz() {}

                fn quux() {/*caret*/}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test meaningful bench configuration name`() {
        testProject {
            lib("foo", "src/lib.rs", "mod bar;")
            file("src/bar/mod.rs", """
                mod tests {
                    fn quux() /*caret*/{}

                    #[bench] fn baz() {}
                }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test bench producer adds bin name`() {
        testProject {
            bin("foo", "src/bin/foo.rs", """
                #[bench]
                fn bench_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test bench configuration uses default environment`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[bench]
                fn bench_foo() { as/*caret*/sert!(true); }
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

    fun `test bench producer works for multiple files`() {
        testProject {
            bench("foo", "benches/foo.rs", """
                #[bench] fn bench_foo() {}
            """)

            bench("bar", "benches/bar.rs", """
                #[bench] fn bench_bar() {}
            """)

            bench("baz", "benches/baz.rs", """
                #[bench] fn bench_baz() {}
            """)
        }

        openFileInEditor("benches/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("benches/baz.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
    }

    fun `test bench producer ignores selected files that contain no benches`() {
        testProject {
            bench("foo", "benches/foo.rs", """
                #[bench] fn bench_foo() {}
            """)

            bench("bar", "benches/bar.rs", """
                fn bench_bar() {}
            """)

            bench("baz", "benches/baz.rs", """
                #[bench] fn bench_baz() {}
            """)
        }

        openFileInEditor("benches/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("benches/bar.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
    }

    fun `test bench producer works for benches source root`() {
        testProject {
            bench("foo", "benches/foo.rs", """
                #[bench] fn bench_foo() {}
            """)
        }

        openFileInEditor("benches/foo.rs")
        val sourceRoot = myFixture.file.containingDirectory
        checkOnFiles(sourceRoot)
    }

    fun `test bench producer works for directories inside benches source root`() {
        testProject {
            bench("foo", "benches/dir/foo.rs", """
                #[bench] fn bench_foo() {}
            """)

            bench("bar", "benches/dir/bar.rs", """
                fn bench_bar() {}
            """)

            bench("baz", "benches/dir/baz.rs", """
                #[bench] fn bench_baz() {}
            """)
        }

        openFileInEditor("benches/dir/foo.rs")
        val dir = myFixture.file.containingDirectory
        checkOnFiles(dir)
    }

    fun `test bench producer doesn't works for directories without benches`() {
        testProject {
            bench("foo", "benches/foo.rs", """
                fn foo() {}
            """)
        }

        openFileInEditor("benches/foo.rs")
        val dir = myFixture.file.containingDirectory
        checkOnFiles(dir)
    }
}
