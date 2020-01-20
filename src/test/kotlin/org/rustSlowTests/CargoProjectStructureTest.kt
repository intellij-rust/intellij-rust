/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectViewTestUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoProjectTreeStructure
import org.rust.cargo.project.toolwindow.CargoProjectTreeStructure.CargoSimpleNode
import org.rust.cargo.project.toolwindow.CargoProjectsTree
import org.rust.fileTree

class CargoProjectStructureTest : RsWithToolchainTestBase() {

    fun `test targets`() = doTest("""
        Root
         Project
          Targets
           Target(bench[bench])
           Target(example[example])
           Target(foo[bin])
           Target(foo[lib])
           Target(test[test])
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
        val structure = CargoProjectTreeStructure(
            CargoProjectsTree(),
            testRootDisposable,
            project.cargoProjects.allProjects.toList()
        )
        assertStructureEqual(structure, expectedTreeStructure.trimIndent() + "\n")
    }

    /**
     * Same as [ProjectViewTestUtil.assertStructureEqual], but uses [CargoSimpleNode.toTestString] instead of [CargoSimpleNode.toString].
     */
    private fun assertStructureEqual(structure: AbstractTreeStructure, expected: String) {
        ProjectViewTestUtil.checkGetParentConsistency(structure, structure.rootElement)
        val actual = PlatformTestUtil.print(structure, structure.rootElement) {
            if (it is CargoSimpleNode) it.toTestString() else it.toString()
        }
        assertEquals(expected, actual)
    }
}
