/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig

import org.rust.cargo.toolchain.RustChannel
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

    fun `test single bench configuration 1`() {
        if (channel != RustChannel.NIGHTLY) return

        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #![feature(test)]

                    extern crate test;

                    use test::Bencher;

                    fn main() {
                        println!("Hello, world!");
                    }

                    #[bench]
                    fn foo(b: &mut Bencher) {
                        /*caret*/
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createBenchRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "foo" }""" in result.stdout)
        check("""{ "type": "bench", "name": "foo", "median": 0, "deviation": 0 }""" in result.stdout)
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

    fun `test single bench configuration 2`() {
        if (channel != RustChannel.NIGHTLY) return

        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #![feature(test)]

                    extern crate test;

                    fn main() {
                        println!("Hello, world!");
                    }

                    #[cfg(test)]
                    mod benches {
                        use test::Bencher;

                        #[bench]
                        fn foo(b: &mut Bencher) {
                            /*caret*/
                        }

                        #[bench]
                        fn bar(b: &mut Bencher) {}
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createBenchRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "benches::foo" }""" in result.stdout)
        check("""{ "type": "test", "event": "started", "name": "benches::bar" }""" !in result.stdout)
        check("""{ "type": "suite", "event": "ok", "passed": 0, "failed": 0, "ignored": 0, "measured": 1, "filtered_out": 1, "exec_time": """ in result.stdout)
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

    fun `test mod bench configuration`() {
        if (channel != RustChannel.NIGHTLY) return

        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #![feature(test)]

                    extern crate test;

                    fn main() {
                        println!("Hello, world!");
                    }

                    #[cfg(test)]
                    mod benches {
                        use test::Bencher;
                        /*caret*/

                        #[bench]
                        fn foo(b: &mut Bencher) {}

                        #[bench]
                        fn bar(b: &mut Bencher) {}
                    }
                """)
            }
        }.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)

        val configuration = createBenchRunConfigurationFromContext()
        val result = executeAndGetOutput(configuration)
        check("""{ "type": "test", "event": "started", "name": "benches::foo" }""" in result.stdout)
        check("""{ "type": "test", "event": "started", "name": "benches::bar" }""" in result.stdout)
        check("""{ "type": "suite", "event": "ok", "passed": 0, "failed": 0, "ignored": 0, "measured": 2, "filtered_out": 0, "exec_time": """ in result.stdout)
    }

    fun `test redirect input (absolute path)`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    use std::io::{self, BufRead};

                    fn main() {
                        let stdin = io::stdin();
                        let mut iter = stdin.lock().lines();
                        println!("{}", iter.next().unwrap().unwrap());
                        println!("{}", iter.next().unwrap().unwrap());
                    }
                """)
            }

            file("in.txt", """
                1. aaa
                2. bbb
                3. ccc
            """)
        }.create()

        val configuration = createConfiguration()
            .apply {
                isRedirectInput = true
                redirectInputPath = workingDirectory?.resolve("in.txt").toString()
            }

        val result = executeAndGetOutput(configuration)
        val stdout = result.stdout
        check("1. aaa" in stdout)
        check("2. bbb" in stdout)
        check("3. ccc" !in stdout)
    }

    fun `test redirect input (relative path)`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    use std::io::{self, BufRead};

                    fn main() {
                        let stdin = io::stdin();
                        let mut iter = stdin.lock().lines();
                        println!("{}", iter.next().unwrap().unwrap());
                        println!("{}", iter.next().unwrap().unwrap());                    }
                """)
            }

            file("in.txt", """
                1. aaa
                2. bbb
                3. ccc
            """)
        }.create()

        val configuration = createConfiguration()
            .apply {
                isRedirectInput = true
                redirectInputPath = "in.txt"
            }

        val result = executeAndGetOutput(configuration)
        val stdout = result.stdout
        check("1. aaa" in stdout)
        check("2. bbb" in stdout)
        check("3. ccc" !in stdout)
    }

    fun `test toolchain override`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    #![feature(if_let)]

                    fn main() {
                        println!("Hello, world!");
                    }
                """)
            }
        }.create()
        val configuration = createConfiguration("+nightly run")
        val result = executeAndGetOutput(configuration)

        check("Hello, world!" in result.stdout)
    }
}
