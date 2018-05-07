/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.testFramework.PlatformTestUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoProjectStructure
import org.rust.fileTree

class CargoProjectStructureTest : RustWithToolchainTestBase() {

    fun `test targets`() = doTest("""
        Root
         Project
          Targets
           Target(foo[lib])
           Target(foo[bin])
           Target(example[example])
           Target(test[test])
           Target(bench[bench])
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", "")
            rust("lib.rs", "")
        }
        dir("benches") {
            rust("bench.rs", "")
        }
        dir("examples") {
            rust("example.rs", "")
        }
        dir("tests") {
            rust("test.rs", "")
        }
    }

    fun `test workspace members`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          WorkspaceMember(bar)
           Targets
            Target(bar[bin])
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []

            [workspace]
            members = [
                "inner-project"
            ]
        """)

        dir("src") {
            rust("main.rs", "fn main() {}")
        }
        dir("inner-project") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", "fn main() {}")
            }
        }
    }

    private fun doTest(expectedTreeStructure: String, builder: FileTreeBuilder.() -> Unit) {
        fileTree(builder).create()
        val structure = CargoProjectStructure(project.cargoProjects.allProjects.toList())
        PlatformTestUtil.assertTreeStructureEquals(structure, expectedTreeStructure.trimIndent())
    }
}
