/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import org.intellij.lang.annotations.Language

class CargoTestNodeInfoTest : CargoTestRunnerTestBase() {

    fun `test int diff`() = doAvailableTest("""
       assert_eq!(1, 2);
    """, "", Diff("1", "2"))

    fun `test char diff`() = doAvailableTest("""
       assert_eq!('a', 'c');
    """, "", Diff("a", "c"))

    fun `test string diff`() = doAvailableTest("""
       assert_eq!("aaa", "bbb");
    """, "", Diff("aaa", "bbb"))

    fun `test multiline string diff`() = doAvailableTest("""
       assert_eq!("a\naa", "bbb");
    """, "", Diff("a\naa", "bbb"))

    fun `test assert_eq with message`() = doAvailableTest("""
       assert_eq!(1, 2, "`1` != `2`");
    """, "`1` != `2`", Diff("1", "2"))

    fun `test no diff`() = doAvailableTest("""
       assert!("aaa" != "aaa");
    """, """assertion failed: "aaa" != "aaa"""")

    fun `test assert with message`() = doAvailableTest("""
       assert!("aaa" != "aaa", "message");
    """, "message")

    fun `test assert_ne`() = doAvailableTest("""
       assert_ne!(123, 123);
    """, "")

    fun `test assert_ne with message`() = doAvailableTest("""
       assert_ne!(123, 123, "123 == 123");
    """, "123 == 123")

    private fun doAvailableTest(@Language("Rust") testFnText: String, message: String, diff: Diff? = null) {
        val testNode = getTestNode(testFnText)
        assertEquals(message, testNode.errorMessage)
        if (diff != null) {
            val diffProvider = testNode.diffViewerProvider ?: error("Diff should be not null")
            assertEquals(diff.actual, diffProvider.left)
            assertEquals(diff.expected, diffProvider.right)
        }
    }

    private fun getTestNode(testFnText: String): SMTestProxy {
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
        assertFalse("Test should fail", testNode.isPassed)
        return testNode
    }

    private data class Diff(val expected: String, val actual: String)
}
