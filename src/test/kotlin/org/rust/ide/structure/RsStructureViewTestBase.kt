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
import org.rust.lang.core.macros.macroExpansionManagerIfCreated
import javax.swing.JTree
import javax.swing.tree.TreePath

abstract class RsStructureViewTestBase : RsTestBase() {
    protected fun doTestSingleAction(
        @Language("Rust") code: String,
        expected: String,
        fileName: String = "main.rs",
        actions: StructureViewComponent.() -> Unit,
    ) = doTestStructureView(code, fileName) {
        val normExpected = expected.trimMargin()
        actions()
        assertTreeEqual(tree, normExpected)
    }

    protected fun doTestStructureView(
        @Language("Rust") code: String,
        fileName: String = "main.rs",
        actions: StructureViewComponent.() -> Unit,
    ) {
        myFixture.configureByText(fileName, code)
        myFixture.project.macroExpansionManagerIfCreated?.updateInUnitTestMode()
        myFixture.testStructureView {
            it.actions()
        }
    }

    protected fun assertTreeEqual(tree: JTree, expected: String) {
        val printInfo = Queryable.PrintInfo(
            arrayOf(RsStructureViewElement.NAME_KEY),
            arrayOf(RsStructureViewElement.VISIBILITY_KEY)
        )
        PlatformTestUtil.expandAll(tree)
        val treeStringPresentation = PlatformTestUtil.print(tree, TreePath(tree.model.root), printInfo, false)
        assertEquals(expected.trim(), treeStringPresentation.trim())
    }
}
