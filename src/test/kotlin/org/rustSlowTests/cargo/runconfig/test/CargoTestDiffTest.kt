/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.testframework.stacktrace.DiffHyperlink
import org.intellij.lang.annotations.Language

class CargoTestDiffTest : CargoTestRunnerTestBase() {

    fun `test int diff`() = doAvailableTest("""
       assert_eq!(1, 2);
    """, "1", "2")

    fun `test char diff`() = doAvailableTest("""
       assert_eq!('a', 'c');
    """, "a", "c")

    fun `test string diff`() = doAvailableTest("""
       assert_eq!("aaa", "bbb");
    """, "aaa", "bbb")

    fun `test multiline string diff`() = doAvailableTest("""
       assert_eq!("a\naa", "bbb");
    """, "a\naa", "bbb")

    fun `test assert with additional message`() = doAvailableTest("""
       assert_eq!(1, 2, "`1` != `2`");
    """, "1", "2")

    fun `test no diff`() = doUnavailableTest("""
       assert!("aaa" != "aaa");
    """)

    fun `test no diff for assert_ne`() = doUnavailableTest("""
       assert_ne!(123, 123);
    """)

    private fun doAvailableTest(@Language("Rust") testFnText: String, expected: String, actual: String) {
        val diff = getDiff(testFnText) ?: error("Diff should be not null")
        assertEquals(actual, diff.left)
        assertEquals(expected, diff.right)
    }

    private fun doUnavailableTest(@Language("Rust") testFnText: String) {
        val diff = getDiff(testFnText)
        assertNull(diff)
    }

    private fun getDiff(testFnText: String): DiffHyperlink? {
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
        return testNode.diffViewerProvider
    }
}
