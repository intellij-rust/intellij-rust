package org.rust.cargo

import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.project.settings.toolchain
import org.rust.fileTree

class RsCargoCheckAnnotatorTest : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/check/fixtures"

    fun testZeroErrorCodeIfProjectHasNoErrors() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }
                """)
            }
        }.create()
        val dir = cargoProjectDirectory.path
        val cmd = myModule.project.toolchain!!.cargo(dir).checkCommandline()
        val result = myModule.project.toolchain!!.cargo(dir).checkProject(testRootDisposable)

        if (result.exitCode != 0) {
            TestCase.fail("Expected zero error code, but got ${result.exitCode}. " +
                "cmd = ${cmd.commandLineString}, stdout = ${result.stdout}, stderr = ${result.stderr}")
        }
    }

    fun testNonZeroErrorCodeIfProjectHasErrors() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("main.rs", """
                    fn main() {
                        println!("Hello, world!") 1;
                    }
                """)
            }
        }.create()

        val result = myModule.project.toolchain!!.cargo(cargoProjectDirectory.path).checkProject(testRootDisposable)
        assertThat(result.exitCode).isNotEqualTo(0)
    }
}
