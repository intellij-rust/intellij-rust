/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.WithExperimentalFeatures
import org.rust.fileTree
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPath
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase

@WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS)
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

    fun `test include in workspace project`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)
            rust("build.rs", BUILD_RS)
            dir("src") {
                rust("main.rs", MAIN_RS)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    fun `test do not overflow stack 1`() {
        buildProject {
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
        }.checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    // https://github.com/intellij-rust/intellij-rust/issues/4579
    fun `test do not overflow stack 2`() {
        buildProject {
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
        }.checkReferenceIsResolved<RsPath>("intellij-rust-test-1/src/lib.rs")
    }

    fun `test include in dependency`() {
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

    fun `test include with build script info with invalid code`() {
        assertTrue(Registry.`is`("org.rust.cargo.evaluate.build.scripts.wrapper"))
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
                        some syntax errors here
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/lib.rs")
    }

    fun `test generated cfg option`() {
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
            .let { rustupFixture.toolchain?.toRemotePath(it) }

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

    fun `test generated feature`() {
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
            .let { rustupFixture.toolchain?.toRemotePath(it) }

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

    fun `test custom generated feature`() {
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
            .let { rustupFixture.toolchain?.toRemotePath(it) }

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

    fun `test generated cfg option with the same name as compiler one`() {
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
            .let { rustupFixture.toolchain?.toRemotePath(it) }

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

    fun `test generated custom feature with the same name as compiler one`() {
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
            .let { rustupFixture.toolchain?.toRemotePath(it) }

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

    fun `test generated environment variables`() {
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

    fun `test generated environment variables 2`() {
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

    fun `test include without file name in literal`() {
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

    fun `test include without file name in literal 2`() {
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

    fun `test do not fail on compilation error`() {
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

    fun `test custom target directory location`() {
        val customTargetDir = tempDirFixture.getFile(".")!!.path
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", MAIN_RS)
            }
            dir(".cargo") {
                toml("config", """
                    [build]
                    target-dir = "$customTargetDir"
                """)
            }
            rust("build.rs", BUILD_RS)
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../hello.rs")
    }

    fun `test workspace with package`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test-1"
                version = "0.1.0"
                authors = []

                [workspace]
                members = ["intellij-rust-test-2"]
            """)
            dir("src") {
                rust("main.rs", "fn main() {}")
            }
            dir("intellij-rust-test-2") {
                toml("Cargo.toml", """
                    [package]
                    name = "intellij-rust-test-2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", MAIN_RS)
                }
                rust("build.rs", BUILD_RS)
            }
        }.checkReferenceIsResolved<RsPath>("intellij-rust-test-2/src/main.rs", toFile = ".../hello.rs")
    }

    // https://github.com/intellij-rust/intellij-rust/issues/8057
    fun `test generated impl block`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    include!(concat!(env!("OUT_DIR"), "/hello.rs"));

                    fn main() {
                        Hello.hello();
                    }          //^
                """)
            }
            rust("build.rs", """
                use std::{fs, path, env};

                fn main() {
                    let content = "\
                    pub struct Hello;
                    impl Hello {
                        pub fn hello(&self) {
                            println!(\"Hello!\");
                        }
                    }
                    ";

                    let out_dir = env::var_os("OUT_DIR").unwrap();
                    let path = path::Path::new(&out_dir).join("hello.rs");
                    fs::write(&path, content).unwrap();
                }
            """)
        }.checkReferenceIsResolved<RsMethodCall>("src/main.rs", toFile = ".../hello.rs")
    }

    companion object {
        @Language("Rust")
        private const val MAIN_RS = """
            include!(concat!(env!("OUT_DIR"), "/hello.rs"));
            fn main() {
                println!("{}", message());
                               //^
            }
        """

        @Language("Rust")
        private const val BUILD_RS = """
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
        """
    }
}
