/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectViewTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoProjectTreeStructure
import org.rust.cargo.project.toolwindow.CargoProjectTreeStructure.CargoSimpleNode
import org.rust.cargo.project.toolwindow.CargoProjectsTree
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.fileTree
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class CargoProjectsTreeTest : RsWithToolchainTestBase() {

    fun `test targets`() = doTest("""
        Root
         Project
          Targets
           Target(bench[bench])
           Target(build-script-build[custom-build])
           Target(example[example])
           Target(foo[bin])
           Target(foo[lib])
           Target(lib_example[example])
           Target(test[test])
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []

            [[example]]
            name = "example"

            [[example]]
            name = "lib_example"
            crate-type = ["lib"]
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
            rust("lib_example.rs", "")
        }
        dir("tests") {
            rust("test.rs", "")
        }
        rust("build.rs", "")
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

    fun `test run configurations`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "my_package"
                version = "0.1.0"
                authors = []

                [[example]]
                name = "my_example"

                [[example]]
                name = "my_lib_example"
                crate-type = ["lib"]
            """)

            dir("src") {
                rust("main.rs", "")
                rust("lib.rs", "")
            }
            dir("benches") {
                rust("my_bench.rs", "")
            }
            dir("examples") {
                rust("my_example.rs", "")
                rust("my_lib_example.rs", "")
            }
            dir("tests") {
                rust("my_test.rs", "")
            }
            rust("build.rs", "")
        }.create()

        fun Any.treePathSegmentToString(): String {
            return ((this as? DefaultMutableTreeNode)?.userObject as? CargoSimpleNode)?.toTestString() ?: toString()
        }

        fun searchTreePath(tree: JTree, stringPath: List<String>): TreePath? {
            var treePath: TreePath? = null
            TreeUtil.visitVisibleRows(tree) {
                if (it.path.size != stringPath.size) return@visitVisibleRows TreeVisitor.Action.CONTINUE
                if (it.path.zip(stringPath).all { (first, second) -> first.treePathSegmentToString() == second }) {
                    treePath = it
                    return@visitVisibleRows TreeVisitor.Action.INTERRUPT
                }
                TreeVisitor.Action.CONTINUE
            }
            return treePath
        }

        var latestConfigurationInfo: ConfigurationInfo? = null
        val tree = TestCargoProjectsTree { latestConfigurationInfo = it }
        CargoProjectTreeStructure(
            tree,
            testRootDisposable,
            project.cargoProjects.allProjects.toList()
        )

        val basePath = listOf("Root", "Project", "Targets")

        val expected = listOf(
            "Target(my_package[lib])" to ConfigurationInfo("build", "Build my_package"),
            "Target(my_package[bin])" to ConfigurationInfo("run", "Run my_package"),
            "Target(my_test[test])" to ConfigurationInfo("test", "Test my_test"),
            "Target(my_bench[bench])" to ConfigurationInfo("bench", "Bench my_bench"),
            "Target(my_example[example])" to ConfigurationInfo("run", "Run my_example"),
            "Target(my_lib_example[example])" to ConfigurationInfo("build", "Build my_lib_example"),
            "Target(build-script-build[custom-build])" to null
        )

        PlatformTestUtil.expandAll(tree)
        for ((lastSegment, expectedConfiguration) in expected) {
            val expectedPath = basePath + lastSegment
            val treePath = searchTreePath(tree, expectedPath) ?: error("Failed to find $expectedPath")
            tree.selectionPath = treePath

            tree.makeDoubleClick()
            assertEquals(expectedConfiguration, latestConfigurationInfo)
            latestConfigurationInfo = null
        }
    }

    private fun Component.makeDoubleClick() {
        val event = MouseEvent(
            this,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            0,
            0,
            /* clickCount = */ 2,
            false,
            MouseEvent.BUTTON1
        )
        dispatchEvent(event)
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

    private data class ConfigurationInfo(val command: String, val configurationName: String)

    private class TestCargoProjectsTree(private val consumer: (ConfigurationInfo) -> Unit) : CargoProjectsTree() {

        override fun run(commandLine: CargoCommandLine, project: CargoProject, name: String) {
            consumer(ConfigurationInfo(commandLine.command, name))
        }
    }
}
