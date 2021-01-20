/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectViewTestUtil
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

abstract class RsProjectViewTestBase : RsWithToolchainTestBase() {

    override fun setUp() {
        super.setUp()
        ProjectViewTestUtil.setupImpl(project, true)
    }

    fun `test external library node`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", "")
            }
        }

        checkExternalLibrariesNode("""
            -External Libraries
             +stdlib $presentableStdlibVersion
        """)
    }

    fun `test external library node with external dependencies`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                code-generation-example = "^0.1.0"
            """)
            dir("src") {
                rust("main.rs", "")
            }
        }

        checkExternalLibrariesNode("""
            -External Libraries
             +code-generation-example 0.1.0
             +stdlib $presentableStdlibVersion
        """)
    }

    private val presentableStdlibVersion: String
        get() = project.cargoProjects.allProjects.first().rustcInfo!!.version!!.semver.parsedVersion

    private fun checkExternalLibrariesNode(expected: String) {
        val projectView = ProjectView.getInstance(project)
        projectView.changeView(ProjectViewPane.ID)
        projectView.refresh()
        val tree = projectView.currentProjectViewPane.tree

        val path = getExternalLibraries(tree)
        tree.expandPath(path)
        PlatformTestUtil.waitWhileBusy(tree)

        assertTreeNodeEqual(tree, path, expected)
    }

    private fun getExternalLibraries(tree: JTree): TreePath {
        PlatformTestUtil.waitWhileBusy(tree)
        val root = tree.model.root
        for (index in 0 until tree.model.getChildCount(root)) {
            val childNode = tree.model.getChild(root, index) as? DefaultMutableTreeNode ?: continue
            if (childNode.userObject is ExternalLibrariesNode) {
                return TreePath(arrayOf(root, childNode))
            }
        }
        error("Failed to find External Library node")
    }

    private fun assertTreeNodeEqual(tree: JTree, path: TreePath, expected: String) {
        val actual = PlatformTestUtil.print(tree, path, null, false)
        assertEquals(expected.trimIndent(), actual)
    }
}
