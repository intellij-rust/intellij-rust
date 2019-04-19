/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.testframework.sm.runner.SMTestProxy

class CargoTestRunnerTest : CargoTestRunnerTestBase() {

    fun `test test statuses`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    /*caret*/

                    #[test]
                    fn test_should_pass() {}

                    #[test]
                    fn test_should_fail() {
                        assert_eq!(1, 2)
                    }

                    #[test]
                    #[ignore]
                    fn test_ignored() {}
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_ignored(~)
            ..test_should_fail(-)
            ..test_should_pass(+)
        """)
    }

    fun `test not executed tests are not shown`() {
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
                    fn test_should_pass() {}

                    #[test]
                    fn test_should_fail() {
                        /*caret*/
                        assert_eq!(1, 2)
                    }

                    #[test]
                    #[ignore]
                    fn test_ignored() {}
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_should_fail(-)
        """)
    }

    fun `test multiple failed tests`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    /*caret*/

                    #[test]
                    fn test_should_fail_1() {
                        assert_eq!(1, 2)
                    }

                    #[test]
                    fn test_should_fail_2() {
                        assert_eq!(2, 3)
                    }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_should_fail_1(-)
            ..test_should_fail_2(-)
        """)
    }

    fun `test tests in submodules`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #[cfg(test)]
                    mod suite_should_fail {
                        /*caret*/

                        #[test]
                        fn test_should_pass() {}

                        mod nested_suite_should_fail {
                            #[test]
                            fn test_should_pass() {}

                            #[test]
                            fn test_should_fail() { panic!(":(") }
                        }

                        mod nested_suite_should_pass {
                            #[test]
                            fn test_should_pass() {}
                        }
                    }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..suite_should_fail(-)
            ...nested_suite_should_fail(-)
            ....test_should_fail(-)
            ....test_should_pass(+)
            ...nested_suite_should_pass(+)
            ....test_should_pass(+)
            ...test_should_pass(+)
        """)
    }

    fun `test test in custom bin target`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [[bin]]
                name = "main"
                path = "src/main.rs"
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}

                    #[test]
                    fn test_should_pass() { /*caret*/ }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](+)
            .main(+)
            ..test_should_pass(+)
        """)
    }

    fun `test test in custom test target`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [[test]]
                name = "tests"
                path = "tests/tests.rs"
            """)

            dir("tests") {
                rust("tests.rs", """
                    #[test]
                    fn test_should_pass() { /*caret*/ }
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](+)
            .tests(+)
            ..test_should_pass(+)
        """)
    }

    fun `test test location`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [[test]]
                name = "tests"
                path = "tests/tests.rs"
            """)

            dir("tests") {
                rust("tests.rs", """
                    /*caret*/

                    #[cfg(test)]
                    mod test_mod {
                        #[test]
                        fn test() {}
                    }

                    #[test]
                    fn test() {}
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        val root = executeAndGetTestRoot(configuration)

        val test = root.findTestByName("tests::test")
        assertTrue("cargo:test://tests-[a-f0-9]{16}::test".toRegex().matches(test.locationUrl ?: ""))

        val mod = root.findTestByName("tests::test_mod")
        assertTrue("cargo:test://tests-[a-f0-9]{16}::test_mod".toRegex().matches(mod.locationUrl ?: ""))

        val testInner = root.findTestByName("tests::test_mod::test")
        assertTrue("cargo:test://tests-[a-f0-9]{16}::test_mod::test".toRegex().matches(testInner.locationUrl ?: ""))
    }

    fun `test test duration`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [[test]]
                name = "tests"
                path = "tests/tests.rs"
            """)

            dir("tests") {
                rust("tests.rs", """
                    use std::thread;
                    /*caret*/

                    #[test]
                    fn test1() {
                        thread::sleep_ms(2000);
                    }

                    #[test]
                    fn test2() {}
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        val configuration = createTestRunConfigurationFromContext()
        val root = executeAndGetTestRoot(configuration)

        val test1 = root.findTestByName("tests::test1")
        check(test1.duration!! > 1000) { "The `test1` duration too short" }

        val test2 = root.findTestByName("tests::test2")
        check(test2.duration!! < 100) { "The `test2` duration too long" }

        val mod = root.findTestByName("tests")
        check(mod.duration!! == test1.duration!! + test2.duration!!) {
            "The `tests` mod duration is not the sum of durations of containing tests"
        }
    }

    private fun checkTestTree(expectedFormattedTestTree: String) {
        val configuration = createTestRunConfigurationFromContext()
        val root = executeAndGetTestRoot(configuration)
        assertEquals(expectedFormattedTestTree.trimIndent(), getFormattedTestTree(root))
    }

    companion object {
        private fun getFormattedTestTree(testTreeRoot: SMTestProxy.SMRootTestProxy): String =
            buildString {
                if (testTreeRoot.wasTerminated()) {
                    append("Test terminated")
                    return@buildString
                }
                formatLevel(testTreeRoot)
            }

        private fun StringBuilder.formatLevel(test: SMTestProxy, level: Int = 0) {
            append(".".repeat(level))
            append(test.name)
            when {
                test.wasTerminated() -> append("[T]")
                test.isPassed -> append("(+)")
                test.isIgnored -> append("(~)")
                else -> append("(-)")
            }

            for (child in test.children) {
                append('\n')
                formatLevel(child, level + 1)
            }
        }
    }
}
