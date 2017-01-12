package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import org.rust.lang.RsTestBase
import java.io.File

abstract class RsDocumentationProviderTest : RsTestBase() {

    protected fun compareByHtml(block: (PsiElement, PsiElement?) -> String?) {
        val expectedFile = File("$testDataPath/${fileName.replace(".rs", ".html")}")
        val expected = FileUtil.loadFile(expectedFile).trim()

        myFixture.configureByFile(fileName)
        val originalElement = myFixture.elementAtCaret
        val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
        val actual = block(element, originalElement)?.trim()
        assertSameLines(expected, actual)
    }
}
