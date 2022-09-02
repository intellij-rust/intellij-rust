/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.rust.TestProject
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.toPsiDirectory

class CargoTestRunnerTest : CargoTestRunnerTestBase() {

    fun `test test statuses`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
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
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_ignored(~)
            ..test_should_fail(-)
            ..test_should_pass(+)
        """, sourceElement)
    }

    fun `test bench statuses`() {
        if (channel != RustChannel.NIGHTLY) return

        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #![feature(test)]
                    /*caret*/

                    extern crate test;

                    use test::Bencher;

                    #[bench]
                    fn bench_should_pass(b: &mut Bencher) {}

                    #[bench]
                    fn bench_should_fail(b: &mut Bencher) {
                        assert_eq!(1, 2)
                    }

                    #[bench]
                    #[ignore]
                    fn bench_ignored(b: &mut Bencher) {}
                """)
            }
        }
        val sourceElement = myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkBenchTree("""
            [root](-)
            .sandbox(-)
            ..bench_ignored(~)
            ..bench_should_fail(-)
            ..bench_should_pass(+)
        """, sourceElement)
    }

    fun `test long output`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("long_output.txt", "0".repeat(9000))

                rust("lib.rs", """
                    #[test]
                    fn test_long_output() {
                        println!("{}", include_str!("long_output.txt"));
                    }
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](+)
            .sandbox(+)
            ..test_long_output(+)
        """, sourceElement)
    }

    fun `test doctests statuses`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    /// ```
                    /// true;
                    /// ```
                    pub fn doctest_should_pass() {}
                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub fn doctest_should_fail() {}
                    /// ```
                    /// true;
                    /// ```
                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub fn many_doctests() {}
                    /// ```
                    /// true;
                    /// ```
                    pub mod non_fn_doctest_should_pass {}
                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub mod non_fn_doctest_should_fail {}
                    /// ```
                    /// true;
                    /// ```
                    pub mod mod_with_doctests_should_pass {
                        /// ```
                        /// true;
                        /// ```
                        pub fn doctest_should_pass() {}
                        /// ```
                        /// assert_eq!(1, 2);
                        /// ```
                        pub fn doctest_should_fail() {}
                    }
                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub mod mod_with_doctests_should_fail {
                        /// ```
                        /// true;
                        /// ```
                        pub fn doctest_should_pass() {}
                        /// ```
                        /// assert_eq!(1, 2);
                        /// ```
                        pub fn doctest_should_fail() {}
                    }
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](-)
            .sandbox (doc-tests)(-)
            ..doctest_should_fail (line 5)(-)
            ..doctest_should_pass (line 1)(+)
            ..many_doctests (line 12)(-)
            ..many_doctests (line 9)(+)
            ..mod_with_doctests_should_fail (line 37)(-)
            ..mod_with_doctests_should_fail(-)
            ...doctest_should_fail (line 45)(-)
            ...doctest_should_pass (line 41)(+)
            ..mod_with_doctests_should_pass (line 24)(+)
            ..mod_with_doctests_should_pass(-)
            ...doctest_should_fail (line 32)(-)
            ...doctest_should_pass (line 28)(+)
            ..non_fn_doctest_should_fail (line 20)(-)
            ..non_fn_doctest_should_pass (line 16)(+)
        """, sourceElement)
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
                rust("lib.rs", """
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

                    /// ```
                    /// true;
                    /// ```
                    pub fn doctest_should_pass() {}
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

    fun `test regular tests and doc tests`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[test]
                    fn test_should_pass() {}

                    #[test]
                    fn test_should_fail() {
                        assert_eq!(1, 2)
                    }

                    /// ```
                    /// true;
                    /// ```
                    pub fn doctest_should_pass() {}

                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub fn doctest_should_fail() {}
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_should_fail(-)
            ..test_should_pass(+)
            .sandbox (doc-tests)(-)
            ..doctest_should_fail (line 14)(-)
            ..doctest_should_pass (line 9)(+)
        """, sourceElement)
    }

    fun `test doctests only`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[test]
                    fn test_should_pass() {}

                    #[test]
                    fn test_should_fail() {
                        assert_eq!(1, 2)
                    }

                    /// ```
                    /// true;
                    /// ```
                    pub fn doctest_should_pass() {}

                    /// ```
                    /// assert_eq!(1, 2);
                    /// ```
                    pub fn doctest_should_fail() {}
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](-)
            .sandbox (doc-tests)(-)
            ..doctest_should_fail (line 14)(-)
            ..doctest_should_pass (line 9)(+)
        """, sourceElement, onlyDoctests = true)
    }

    fun `test multiple failed tests`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
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
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](-)
            .sandbox(-)
            ..test_should_fail_1(-)
            ..test_should_fail_2(-)
        """, sourceElement)
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
                rust("lib.rs", """
                    #[cfg(test)]
                    mod /*caret*/suite_should_fail {
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

    fun `test tests in mod decl`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    mod tests/*caret*/;
                    #[test]
                    fn test_should_pass() {}
                """)
                rust("tests.rs", """
                    #[test]
                    fn test_should_pass() {}
                """)
            }
        }
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        checkTestTree("""
            [root](+)
            .sandbox(+)
            ..tests(+)
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

    fun `test tests in project`() {
        buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "package1",
                    "package2",
                ]
            """)

            dir("package1") {
                toml("Cargo.toml", """
                    [package]
                    name = "package1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                        #[test]
                        fn test() {}
                    """)

                    rust("main.rs", """
                        fn main() {}

                        #[test]
                        fn test() {}
                    """)
                }

                dir("tests") {
                    rust("package1.rs", """
                        #[test]
                        fn test() {}
                    """)
                }
            }

            dir("package2") {
                toml("Cargo.toml", """
                    [package]
                    name = "package2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                        #[test]
                        fn test() {}
                    """)

                    rust("main.rs", """
                        fn main() {}

                        #[test]
                        fn test() {}
                    """)
                }

                dir("tests") {
                    rust("package2.rs", """
                        #[test]
                        fn test() {}
                    """)
                }
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!

        checkTestTree("""
            [root](+)
            .package1(+)
            ..test(+)
            .package1(+)
            ..test(+)
            .package1(+)
            ..test(+)
            .package2(+)
            ..test(+)
            .package2(+)
            ..test(+)
            .package2(+)
            ..test(+)
        """, sourceElement)
    }

    fun `test tests in package`() {
        buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "package1",
                    "package2",
                ]
            """)

            dir("package1") {
                toml("Cargo.toml", """
                    [package]
                    name = "package1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                        #[test]
                        fn test() {}
                    """)

                    rust("main.rs", """
                        fn main() {}

                        #[test]
                        fn test() {}
                    """)
                }

                dir("tests") {
                    rust("package1.rs", """
                        #[test]
                        fn test() {}
                    """)
                }
            }

            dir("package2") {
                toml("Cargo.toml", """
                    [package]
                    name = "package2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                        #[test]
                        fn test() {}
                    """)
                }
            }
        }
        val sourceElement = cargoProjectDirectory.findFileByRelativePath("package1")?.toPsiDirectory(project)!!

        checkTestTree("""
            [root](+)
            .package1(+)
            ..test(+)
            .package1(+)
            ..test(+)
            .package1(+)
            ..test(+)
        """, sourceElement)
    }

    fun `test tests in tests source root`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "package"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[test]
                    fn test() {}
                """)
            }

            dir("tests") {
                rust("tests1.rs", """
                    #[test]
                    fn test() {}
                """)

                rust("tests2.rs", """
                    #[test]
                    fn test() {}
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.findFileByRelativePath("tests")?.toPsiDirectory(project)!!

        checkTestTree("""
            [root](+)
            .tests1(+)
            ..test(+)
            .tests2(+)
            ..test(+)
        """, sourceElement)
    }

    fun `test tests in directory under tests source root`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "package"
                version = "0.1.0"
                authors = []

                [[test]]
                name = "tests1"
                path = "tests/tests1.rs"

                [[test]]
                name = "tests2"
                path = "tests/subdir/tests2.rs"
            """)


            dir("tests") {
                rust("tests1.rs", """
                    #[test]
                    fn test() {}
                """)

                dir("subdir") {
                    rust("tests2.rs", """
                        #[test]
                        fn test() {}
                    """)
                }
            }
        }
        val sourceElement = cargoProjectDirectory.findFileByRelativePath("tests/subdir")?.toPsiDirectory(project)!!

        checkTestTree("""
            [root](+)
            .tests2(+)
            ..test(+)
        """, sourceElement)
    }

    fun `test test location`() {
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
                    fn /*caret*/test() {}
                """)
            }
        }
        checkTestLocation("sandbox::test", testProject)
    }

    fun `test test mod location`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[cfg(test)]
                    mod /*caret*/test_mod {
                        #[test]
                        fn test() {}
                    }
                """)
            }
        }
        checkTestLocation("sandbox::test_mod", testProject)
    }

    fun `test nested test location`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[cfg(test)]
                    mod test_mod {
                        #[test]
                        fn /*caret*/test() {}
                    }

                    #[test]
                    fn test() {}
                """)
            }
        }
        checkTestLocation("sandbox::test_mod::test", testProject)
    }

    fun `test doctest location 1`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    /*caret*//// ```
                    /// 1;
                    /// ```
                    /// ```
                    /// 2;
                    /// ```
                    pub fn doctest() {}
                """)
            }
        }
        checkTestLocation("sandbox (doc-tests)::doctest (line 1)", testProject)
    }

    fun `test doctest location 2`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    /// ```
                    /// 1;
                    /// ```
                    /*caret*//// ```
                    /// 2;
                    /// ```
                    pub fn doctest() {}
                """)
            }
        }
        checkTestLocation("sandbox (doc-tests)::doctest (line 4)", testProject)
    }

    fun `test test duration`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    use std::thread;

                    #[test]
                    fn test1() {
                        thread::sleep_ms(2000);
                    }

                    #[test]
                    fn test2() {}
                """)
            }
        }
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!
        val configuration = createTestRunConfigurationFromContext(PsiLocation.fromPsiElement(sourceElement))
        val root = executeAndGetTestRoot(configuration)

        val test1 = root.findTestByName("sandbox::test1")
        check(test1.duration!! > 1000) { "The `test1` duration too short" }

        val test2 = root.findTestByName("sandbox::test2")
        check(test2.duration!! < 100) { "The `test2` duration too long" }

        val mod = root.findTestByName("sandbox")
        check(mod.duration!! == test1.duration!! + test2.duration!!) {
            "The `sandbox` mod duration is not the sum of durations of containing tests"
        }
    }

    private fun checkTestTree(
        expectedFormattedTestTree: String,
        sourceElement: PsiElement? = null,
        onlyDoctests: Boolean = false
    ) {
        val configuration = createTestRunConfigurationFromContext(PsiLocation.fromPsiElement(sourceElement))
        if (onlyDoctests) {
            val cmd = configuration.clean().ok?.cmd!!
            val cmdWithDoc = cmd.copy(additionalArguments = listOf("--doc") + cmd.additionalArguments)
            configuration.setFromCmd(cmdWithDoc)
        }
        val root = executeAndGetTestRoot(configuration)
        assertEquals(expectedFormattedTestTree.trimIndent(), getFormattedTestTree(root))
    }

    private fun checkBenchTree(
        expectedFormattedBenchTree: String,
        sourceElement: PsiElement? = null
    ) {
        val configuration = createBenchRunConfigurationFromContext(PsiLocation.fromPsiElement(sourceElement))
        val root = executeAndGetTestRoot(configuration)
        assertEquals(expectedFormattedBenchTree.trimIndent(), getFormattedTestTree(root))
    }

    private fun checkTestLocation(testName: String, testProject: TestProject) {
        val expectedText = (testProject.psiFile(testProject.fileWithCaret) as PsiFile).text
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        myFixture.editor.caretModel.moveToOffset(0)
        val sourceElement = cargoProjectDirectory.toPsiDirectory(project)!!
        val configuration = createTestRunConfigurationFromContext(PsiLocation.fromPsiElement(sourceElement))
        val root = executeAndGetTestRoot(configuration)
        val test = root.findTestByName(testName)
        val location = test.getLocation(project, GlobalSearchScope.allScope(project))
        val desc = location?.openFileDescriptor!!
        desc.navigateIn(editor)
        myFixture.checkResult(expectedText)
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
