package org.rust.lang

import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.util.FileStructurePopup
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.hamcrest.CoreMatchers
import org.junit.Assert
import javax.swing.tree.TreePath

class RustStructureViewTest : LightCodeInsightFixtureTestCase() {
    override fun getTestDataPath() = "testData/structure"

    private fun structureContainsElements(vararg expected: String) {
        val fileName = getTestName(true) + ".rs"
        myFixture.configureByFile(fileName)
        val editor = TextEditorProvider.getInstance()!!.getTextEditor(myFixture.editor)
        val popup = ViewStructureAction.createPopup(myFixture.project, editor)
                ?: throw AssertionError("popup mustn't be null")

        try {
            popup.createCenterPanel()
            popup.shallowExpand()
            val text = PlatformTestUtil.print(popup.tree)

            expected.forEach {
                Assert.assertThat(text, CoreMatchers.containsString(it))
            }
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
