/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.Printable
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType
import org.intellij.lang.annotations.Language
import org.rust.cargo.toolchain.RustChannel

class CargoTestNodeInfoTest : CargoTestRunnerTestBase() {

    fun `test int diff`() = checkErrors("""
       assert_eq!(1, 2);
    """, "", Diff("1", "2"))

    fun `test char diff`() = checkErrors("""
       assert_eq!('a', 'c');
    """, "", Diff("a", "c"))

    fun `test string diff`() = checkErrors("""
       assert_eq!("aaa", "bbb");
    """, "", Diff("aaa", "bbb"))

    fun `test multiline string diff`() = checkErrors("""
       assert_eq!("a\naa", "bbb");
    """, "", Diff("a\naa", "bbb"))

    fun `test assert_eq with message`() = checkErrors("""
       assert_eq!(1, 2, "`1` != `2`");
    """, "`1` != `2`", Diff("1", "2"))

    fun `test no diff`() = checkErrors("""
       assert!(1 != 1);
    """, """assertion failed: 1 != 1""")

    fun `test assert with message`() = checkErrors("""
       assert!("aaa" != "aaa", "message");
    """, "message")

    fun `test assert_ne`() = checkErrors("""
       assert_ne!(123, 123);
    """, "")

    fun `test assert_ne with message`() = checkErrors("""
       assert_ne!(123, 123, "123 == 123");
    """, "123 == 123")

    fun `test unescape error messages`() = checkErrors("""
       assert_eq!("a\\\\b", "a\\b", "`a\\\\b` != `a\\b`");
    """, "`a\\\\b` != `a\\b`", Diff("a\\\\b", "a\\b"))

    fun `test don't unescape test output`() = checkOutput("""
        println!("a\\\\b");
    """, "a\\\\b\n")

    fun `test successful output`() = checkOutput("""
        println!("
                  aaa - bbb");
    """, """
          aaa - bbb
    """)

    fun `test failed output`() = checkOutput("""
        println!("
                  aaa - bbb");
        panic!("
                  ccc - ddd");
    """, """
          aaa - bbb


          ccc - ddd
    """, shouldPass = false)

    fun `test root output`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[test]
                    fn test() {
                        /*caret*/
                        let x = 42;
                    }
                """)
            }
        }

        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        configuration.command += " --unknown"
        val root = executeAndGetTestRoot(configuration)
        assertTrue("Testing started" in root.output)
        assertTrue("warning: unused variable: `x`" in root.output)
        assertTrue("Finished" in root.output)
        assertTrue("Running" in root.output)
        assertTrue("error: Unrecognized option: 'unknown'" in root.output)
        assertTrue("Process finished" in root.output)
        assertFalse(root.isTestsReporterAttached)
    }

    fun `test root output (no tests)`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[cfg(tests)] // typo
                    mod tests {
                        #[test]
                        fn test() {
                            /*caret*/
                            let x = 42;
                        }
                    }
                """)
            }
        }

        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        val root = executeAndGetTestRoot(configuration)
        assertTrue("Testing started" in root.output)
        assertFalse("warning: unused variable: `x`" in root.output)
        assertTrue("Finished" in root.output)
        assertTrue("Running" in root.output)
        assertTrue("Process finished" in root.output)
        assertTrue(root.isTestsReporterAttached)
    }

    fun `test bench output`() {
        if (channel != RustChannel.NIGHTLY) return

        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #![feature(test)]

                    extern crate test;

                    use test::Bencher;

                    #[bench]
                    fn bench(b: &mut Bencher) {
                        /*caret*/
                    }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createBenchRunConfigurationFromContext()
        val testNode = executeAndGetTestRoot(configuration).findTestByName("sandbox::bench")
        assertEquals("0 ns/iter (+/- 0)\n", testNode.output)
    }

    private fun checkErrors(
        @Language("Rust") testFnText: String,
        message: String,
        diff: Diff? = null,
        shouldPass: Boolean = false
    ) {
        val testNode = getTestNode(testFnText, shouldPass)
        assertEquals(message, testNode.errorMessage)
        if (diff != null) {
            val diffProvider = testNode.diffViewerProvider ?: error("Diff should be not null")
            assertEquals(diff.actual, diffProvider.left)
            assertEquals(diff.expected, diffProvider.right)
        }
    }

    private fun checkOutput(@Language("Rust") testFnText: String, output: String, shouldPass: Boolean = true) {
        val testNode = getTestNode(testFnText, shouldPass)
        assertEquals(output.trimIndent(), testNode.output.trimIndent())
    }

    private fun getTestNode(testFnText: String, shouldPass: Boolean): SMTestProxy {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #[test]
                    fn test() {/*caret*/
                        $testFnText
                    }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        val testNode = executeAndGetTestRoot(configuration).findTestByName("sandbox::test")
        assertEquals("Test should ${if (shouldPass) "pass" else "fail"}", shouldPass, testNode.isPassed)
        return testNode
    }

    private data class Diff(val expected: String, val actual: String)

    companion object {
        private val AbstractTestProxy.output: String
            get() {
                val printer = MockPrinter()
                printOn(printer)
                return printer.output
            }
    }
}

class MockPrinter : Printer {
    private val _output: StringBuilder = StringBuilder()
    val output: String get() = _output.toString().substringBefore("thread")

    override fun print(text: String, contentType: ConsoleViewContentType) {
        _output.append(text)
    }

    override fun printHyperlink(text: String, info: HyperlinkInfo) {}

    override fun onNewAvailable(printable: Printable) {
        printable.printOn(this)
    }

    override fun mark() {}
}
