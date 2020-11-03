/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.rust.IgnoreInNewResolve
import org.rust.MinRustcVersion
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.fileTree
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsPath
import org.rust.openapiext.runWithEnabledFeature
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase

@IgnoreInNewResolve
class CargoGeneratedItemsResolveTest : RunConfigurationTestBase() {

    private val tempDirFixture = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        tempDirFixture.setUp()
    }

    override fun tearDown() {
        tempDirFixture.tearDown()
        super.tearDown()
    }

    // Disable creation of temp directory with test name
    // because it leads to too long path and compilation of test rust project fails on Windows
    override fun shouldContainTempFiles(): Boolean = false

    @MinRustcVersion("1.32.0")
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
            testProject.findElementInFile<RsPath>("src/main.rs").reference?.resolve() != null
        }
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    @MinRustcVersion("1.32.0")
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
            testProject.findElementInFile<RsPath>("src/main.rs").reference?.resolve() != null
        }
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    @MinRustcVersion("1.32.0")
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
            testProject.findElementInFile<RsPath>("intellij-rust-test-1/src/lib.rs").reference?.resolve() != null
        }
    }

    @MinRustcVersion("1.32.0")
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
            testProject.findElementInFile<RsPath>("src/lib.rs").reference?.resolve() != null
        }
    }

    @MinRustcVersion("1.41.0")
    fun `test include with build script info`() = withEnabledEvaluateBuildScriptsFeature {
        buildProject {
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
        }.checkReferenceIsResolved<RsPath>("src/lib.rs")
    }

    @MinRustcVersion("1.41.0")
    fun `test do not run build-plan if build script info is enough`() = withEnabledFetchOutDirFeature {
        withEnabledEvaluateBuildScriptsFeature {
            Cargo.Testmarks.fetchBuildPlan.checkNotHit {
                buildProject {
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
                }.checkReferenceIsResolved<RsPath>("src/lib.rs")
            }
        }
    }

    fun `test generated cfg option`() = withEnabledEvaluateBuildScriptsFeature {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(has_generated_feature))]
                    mod disabled;
                    #[cfg(has_generated_feature)]
                    mod enabled;

                    #[cfg(not(has_generated_feature))]
                    pub use disabled::function_under_cfg;
                    #[cfg(has_generated_feature)]
                    pub use enabled::function_under_cfg;
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {
                        println!("'has_generated_feature' is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {
                        println!("'has_generated_feature' is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=has_generated_feature");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;
                    use foo::function_under_cfg;
                    fn main() {
                        function_under_cfg();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    fun `test generated feature`() = withEnabledEvaluateBuildScriptsFeature {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(feature = "generated_feature"))]
                    mod disabled;
                    #[cfg(feature = "generated_feature")]
                    mod enabled;

                    #[cfg(not(feature = "generated_feature"))]
                    pub use disabled::function_under_feature;
                    #[cfg(feature = "generated_feature")]
                    pub use enabled::function_under_feature;
                """)
                rust("disabled.rs", """
                    pub fn function_under_feature() {
                        println!("'generated_feature' is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_feature() {
                        println!("'generated_feature' is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=feature=\"generated_feature\"");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;
                    use foo::function_under_feature;
                    fn main() {
                        function_under_feature();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    fun `test custom generated feature`() = withEnabledEvaluateBuildScriptsFeature {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(generated_feature_key = "generated_feature_value"))]
                    mod disabled;
                    #[cfg(generated_feature_key = "generated_feature_value")]
                    mod enabled;

                    #[cfg(not(generated_feature_key = "generated_feature_value"))]
                    pub use disabled::function_under_custom_feature;
                    #[cfg(generated_feature_key = "generated_feature_value")]
                    pub use enabled::function_under_custom_feature;
                """)
                rust("disabled.rs", """
                    pub fn function_under_custom_feature() {
                        println!("custom generated feature is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_custom_feature() {
                        println!("custom generated feature is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=generated_feature_key=\"generated_feature_value\"");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;
                    use foo::function_under_custom_feature;
                    fn main() {
                        function_under_custom_feature();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    fun `test generated cfg option with the same name as compiler one`() = withEnabledEvaluateBuildScriptsFeature {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(windows))]
                    mod disabled;
                    #[cfg(windows)]
                    mod enabled;

                    #[cfg(not(windows))]
                    pub use disabled::function_under_cfg;
                    #[cfg(windows)]
                    pub use enabled::function_under_cfg;
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {
                        println!("custom generated cfg option is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {
                        println!("custom generated cfg option is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=windows");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;
                    use foo::function_under_cfg;
                    fn main() {
                        function_under_cfg();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    fun `test generated custom feature with the same name as compiler one`() = withEnabledEvaluateBuildScriptsFeature {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(target_family = "windows"))]
                    mod disabled;
                    #[cfg(target_family = "windows")]
                    mod enabled;

                    #[cfg(not(target_family = "windows"))]
                    pub use disabled::function_under_cfg;
                    #[cfg(target_family = "windows")]
                    pub use enabled::function_under_cfg;
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {
                        println!("custom generated cfg option is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {
                        println!("custom generated cfg option is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=target_family=\"windows\"");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;
                    use foo::function_under_cfg;
                    fn main() {
                        function_under_cfg();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    @MinRustcVersion("1.32.0")
    fun `test generated environment variables`() = withEnabledEvaluateBuildScriptsFeature {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    include!(concat!("foo/", env!("GENERATED_ENV_DIR"), "/hello.rs"));
                    fn main() {
                        hello();
                         //^
                    }
                """)
                dir("foo") {
                    dir("bar") {
                        rust("hello.rs", """
                            fn hello() {
                                println!("Hello!");
                            }
                        """)
                    }
                }
            }
            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-env=GENERATED_ENV_DIR=bar");
                }
            """)
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../foo/bar/hello.rs")
    }

    @MinRustcVersion("1.32.0")
    fun `test generated environment variables 2`() = withEnabledFetchOutDirFeature {
        withEnabledEvaluateBuildScriptsFeature {
            buildProject {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        include!(concat!(env!("GENERATED_ENV_DIR"), "/hello.rs"));
                        fn main() {
                            println!("{}", message());
                                           //^
                        }
                    """)
                }
                rust("build.rs", """
                    use std::env;
                    use std::fs;
                    use std::fs::File;
                    use std::io::Write;
                    use std::path::Path;

                    fn main() {
                        let out_dir = env::var("OUT_DIR").unwrap();
                        let gen_dir = Path::new(&out_dir).join("gen");
                        if !gen_dir.exists() {
                            fs::create_dir(&gen_dir).unwrap();
                        }
                        let dest_path = gen_dir.join("hello.rs");
                        generate_file(&dest_path, b"
                            pub fn message() -> &'static str {
                                \"Hello, World!\"
                            }
                        ");
                        println!("cargo:rustc-env=GENERATED_ENV_DIR={}", gen_dir.display());
                    }

                    fn generate_file<P: AsRef<Path>>(path: P, text: &[u8]) {
                        let mut f = File::create(path).unwrap();
                        f.write_all(text).unwrap()
                    }
                """)
            }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../gen/hello.rs")
        }
    }

    @MinRustcVersion("1.32.0")
    fun `test include without file name in literal`() = withEnabledFetchOutDirFeature {
        withEnabledEvaluateBuildScriptsFeature {
            buildProject {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        include!(env!("GENERATED_ENV_FILE"));
                        fn main() {
                            println!("{}", message());
                                           //^
                        }
                    """)
                }
                rust("build.rs", """
                    use std::env;
                    use std::fs;
                    use std::fs::File;
                    use std::io::Write;
                    use std::path::Path;

                    fn main() {
                        let out_dir = env::var("OUT_DIR").unwrap();
                        let gen_dir = Path::new(&out_dir).join("gen");
                        if !gen_dir.exists() {
                            fs::create_dir(&gen_dir).unwrap();
                        }
                        let dest_path = gen_dir.join("hello.rs");
                        generate_file(&dest_path, b"
                            pub fn message() -> &'static str {
                                \"Hello, World!\"
                            }
                        ");
                        println!("cargo:rustc-env=GENERATED_ENV_FILE={}", dest_path.display());
                    }

                    fn generate_file<P: AsRef<Path>>(path: P, text: &[u8]) {
                        let mut f = File::create(path).unwrap();
                        f.write_all(text).unwrap()
                    }
                """)
            }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../gen/hello.rs")
        }
    }

    @MinRustcVersion("1.32.0")
    fun `test include without file name in literal 2`() = withEnabledFetchOutDirFeature {
        withEnabledEvaluateBuildScriptsFeature {
            buildProject {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        include!(concat!(env!("OUT_DIR"), "/hello", ".rs"));
                        fn main() {
                            println!("{}", message());
                                           //^
                        }
                    """)
                }
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
            }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../hello.rs")
        }
    }

    @MinRustcVersion("1.32.0")
    fun `test do not fail on compilation error`() = withEnabledEvaluateBuildScriptsFeature {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    pub mod bar;
                    fn main() {
                        println!("{}", bar::message());
                                            //^
                    }
                """)
                rust("bar.rs", """
                    pub fn message() -> String { } // compilation error
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/bar.rs")
    }

    private fun withEnabledFetchOutDirFeature(action: () -> Unit) =
        runWithEnabledFeature(RsExperiments.FETCH_OUT_DIR, action)

    private fun withEnabledEvaluateBuildScriptsFeature(action: () -> Unit) =
        runWithEnabledFeature(RsExperiments.EVALUATE_BUILD_SCRIPTS, action)
}
