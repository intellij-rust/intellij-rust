/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig

import org.rust.MinRustcVersion
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsPath
import org.rust.openapiext.runWithEnabledFeature

@MinRustcVersion("1.32.0")
class RsIncludeMacroResolveTest : RunConfigurationTestBase() {

    fun `test include in workspace project`() = withEnabledFetchOutDirFeature {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)
            rust("build.rs", """
                use std::env;
                use std::fs::File;
                use std::io::Write;
                use std::path::Path;

                fn main() {
                    let out_dir = env::var("OUT_DIR").unwrap();
                    let dest_path = Path::new(&out_dir).join("hello.rs");
                    let mut f = File::create(&dest_path).unwrap();

                    f.write_all(b"
                        pub fn message() -> &'static str {
                            \"Hello, World!\"
                        }",
                    ).unwrap();
                }
            """)
            dir("src") {
                rust("main.rs", """
                    include!(concat!(env!("OUT_DIR"), "/hello.rs"));

                    fn main() {
                        println!("{}", message());
                                        //^
                    }
                """)
            }
        }
        buildProject()

        runWithInvocationEventsDispatching("Failed to resolve the reference") {
            testProject.findElementInFile<RsPath>("src/main.rs").reference.resolve() != null
        }
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    fun `test do not overflow stack 1`() = withEnabledFetchOutDirFeature {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)
            rust("build.rs", """
                use std::env;
                use std::fs::File;
                use std::io::Write;
                use std::path::Path;

                fn main() {
                    let out_dir = env::var("OUT_DIR").unwrap();
                    let dest_path = Path::new(&out_dir).join("main.rs");
                    let mut f = File::create(&dest_path).unwrap();

                    f.write_all(b"
                        pub fn message() -> &'static str {
                            \"Hello, World!\"
                        }",
                    ).unwrap();
                }
            """)
            dir("src") {
                rust("main.rs", """
                    include!(concat!(env!("OUT_DIR"), "/main.rs"));

                    fn main() {
                        println!("{}", message());
                                        //^
                    }
                """)
            }
        }
        buildProject()

        runWithInvocationEventsDispatching("Failed to resolve the reference") {
            testProject.findElementInFile<RsPath>("src/main.rs").reference.resolve() != null
        }
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    fun `test do not overflow stack 2`() = withEnabledFetchOutDirFeature {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "intellij-rust-test-1",
                    "intellij-rust-test-2"
                ]
            """)
            dir("intellij-rust-test-1") {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test-1"
                    version = "0.1.0"
                    authors = []

                """)
                rust("build.rs", """
                    use std::env;
                    use std::fs::File;
                    use std::io::Write;
                    use std::path::Path;

                    fn main() {
                        let out_dir = env::var("OUT_DIR").unwrap();
                        let dest_path = Path::new(&out_dir).join("lib.rs");
                        let mut f = File::create(&dest_path).unwrap();

                        f.write_all(b"
                            pub fn message() -> &'static str {
                                \"Hello, World!\"
                            }",
                        ).unwrap();
                    }
                """)
                dir("src") {
                    rust("lib.rs", """
                        include!(concat!(env!("OUT_DIR"), "/lib.rs"));

                        fn main() {
                            println!("{}", message());
                                            //^
                        }
                    """)
                }
            }
            dir("intellij-rust-test-2") {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test-2"
                    version = "0.1.0"
                    authors = []
                """)
                rust("build.rs", """
                    use std::env;
                    use std::fs::File;
                    use std::path::Path;

                    fn main() {
                        let out_dir = env::var("OUT_DIR").unwrap();
                        let dest_path = Path::new(&out_dir).join("lib.rs");
                        let mut f = File::create(&dest_path).unwrap();
                    }
                """)
                dir("src") {
                    rust("lib.rs", """
                        include!(concat!(env!("OUT_DIR"), "/lib.rs"));
                    """)
                }
            }
        }
        buildProject()

        runWithInvocationEventsDispatching("Failed to resolve the reference") {
            testProject.findElementInFile<RsPath>("intellij-rust-test-1/src/lib.rs").reference.resolve() != null
        }
    }

    fun `test include in dependency`() = withEnabledFetchOutDirFeature {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                code-generation-example = "0.1.0"
            """)
            dir("src") {
                rust("lib.rs", """
                    fn main() {
                        println!("{}", code_generation_example::message());
                                                               //^
                    }
                """)
            }
        }
        buildProject()
        runWithInvocationEventsDispatching("Failed to resolve the reference") {
            testProject.findElementInFile<RsPath>("src/lib.rs").reference.resolve() != null
        }
    }

    private fun withEnabledFetchOutDirFeature(action: () -> Unit) =
        runWithEnabledFeature(RsExperiments.FETCH_OUT_DIR, action)
}
