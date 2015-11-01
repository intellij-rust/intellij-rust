package org.rust.lang.structure

import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.util.FileStructurePopup
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCase
import javax.swing.tree.TreePath

class RustStructureViewTest : RustTestCase() {
    override fun getTestDataPath() = "testData/structure"

    private fun structureContainsElements(vararg expected: String) {
        myFixture.configureByFile(fileName)
        val editor = TextEditorProvider.getInstance()!!.getTextEditor(myFixture.editor)
        val popup = ViewStructureAction.createPopup(myFixture.project, editor)
                ?: throw AssertionError("popup mustn't be null")

        try {
            popup.createCenterPanel()
            popup.shallowExpand()
            val text = PlatformTestUtil.print(popup.tree)

            assertThat(text).contains(*expected)
        } finally {
            Disposer.dispose(popup)
        }
    }

    private fun FileStructurePopup.shallowExpand() {
        tree.expandPath(TreePath(tree.model.root))
    }

    fun testFunctions() {
        structureContainsElements("fn_foo", "method_bar")
    }
}
