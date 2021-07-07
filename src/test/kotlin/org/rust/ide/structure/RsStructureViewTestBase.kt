/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.openapi.ui.Queryable
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import javax.swing.JTree
import javax.swing.tree.TreePath

abstract class RsStructureViewTestBase : RsTestBase() {
    protected fun doTestWithActions(
        @Language("Rust") code: String,
        expected: String,
        fileName: String = "main.rs",
        actions: StructureViewComponent.() -> Unit,
    ) {
        val normExpected = expected.trimMargin()
        myFixture.configureByText(fileName, code)
        myFixture.testStructureView {
            it.actions()
            PlatformTestUtil.expandAll(it.tree)
            assertTreeEqual(it.tree, normExpected)
        }
    }

    private fun assertTreeEqual(tree: JTree, expected: String) {
        val printInfo = Queryable.PrintInfo(
            arrayOf(RsStructureViewElement.NAME_KEY),
            arrayOf(RsStructureViewElement.VISIBILITY_KEY)
        )
        val treeStringPresentation = PlatformTestUtil.print(tree, TreePath(tree.model.root), printInfo, false)
        assertEquals(expected.trim(), treeStringPresentation.trim())
    }
}
