/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig

import org.rust.fileTree

class RunConfigurationTest : RunConfigurationTestBase() {

    fun `test application configuration`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }
                """)
            }
        }.create()
        val configuration = createConfiguration()
        val result = executeAndGetOutput(configuration)

        check("Hello, world!" in result.stdout)
    }

    fun `test single test configuration 1`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }

                    #[test]
                    fn foo() {
                        /*caret*/
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createTestRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "foo" }""" in result.stdout)
    }

    fun `test single test configuration 2`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }

                    #[cfg(test)]
                    mod tests {
                        #[test]
                        fn foo() {
                            /*caret*/
                        }

                        #[test]
                        fn bar() {}
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createTestRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "tests::foo" }""" in result.stdout)
        check("""{ "type": "test", "event": "started", "name": "tests::bar" }""" !in result.stdout)
    }

    fun `test mod test configuration`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }

                    #[cfg(test)]
                    mod tests {
                        /*caret*/
                        #[test]
                        fn foo() {}

                        #[test]
                        fn bar() {}
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createTestRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "tests::foo" }""" in result.stdout)
        check("""{ "type": "test", "event": "started", "name": "tests::bar" }""" in result.stdout)
    }
}
