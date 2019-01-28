/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoProjectStructure
import org.rust.fileTree
import javax.swing.tree.TreeModel

class CargoProjectStructureTest : RsWithToolchainTestBase() {

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
        assertTreeStructureEquals(structure, expectedTreeStructure.trimIndent() + "\n")
    }

    // TODO: use [com.intellij.testFramework.PlatformTestUtil#assertTreeEqual(javax.swing.JTree, java.lang.String)]
    //  Original [com.intellij.testFramework.PlatformTestUtil#assertTreeStructureEquals] was dropped in
    //  https://github.com/JetBrains/intellij-community/commit/6c92cfb65a482f05a87f5e3990b5635010e787f0
    //  So, it's temporarily solution to allow test works as before
    private fun assertTreeStructureEquals(treeModel: TreeModel, expected: String) {
        val structure = object : AbstractTreeStructure() {
            override fun getRootElement(): Any = treeModel.root
            override fun getChildElements(element: Any): Array<Any> =
                TreeUtil.nodeChildren(element, treeModel).toList().toTypedArray()
            override fun getParentElement(element: Any): Any? = (element as AbstractTreeNode<*>).parent
            override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> =
                throw UnsupportedOperationException()
            override fun commit(): Unit = throw UnsupportedOperationException()
            override fun hasSomethingToCommit(): Boolean = throw UnsupportedOperationException()
        }
        assertEquals(expected, PlatformTestUtil.print(structure, treeModel.root, 0, null, -1, ' ', null).toString())
    }
}
