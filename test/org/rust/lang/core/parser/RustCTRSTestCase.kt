package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.ParsingTestCase
import org.junit.Assert
import org.rust.lang.core.RustParserDefinition
import java.io.File

public class RustCTRSTestCase : RustParsingTestCaseBase("ctrs") {

    override fun getTestDataPath() = "testData"

    public fun hasError(file: PsiFile): Boolean {
        var hasErrors = false
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is PsiErrorElement) {
                    hasErrors = true
                    return
                }
                element!!.acceptChildren(this)
            }
        })
        return hasErrors
    }

    fun testCtrs() {
        FileUtil.visitFiles(File(myFullDataPath, "test"), {
            if (it.isFile && it.extension == myFileExt.trimStart('.')) {
                val text = FileUtil.loadFile(it)
                val psi = createPsiFile(it.name, text)
                val expectedError = expectedErrors.contains(it.path)
                val messageTail = "in ${it.path}:\n\n" +
                        "$text\n\n" +
                        "${DebugUtil.psiToString(psi, true)}"
                if (hasError(psi) ) {
                    Assert.assertTrue("New error " + messageTail, expectedError);
                } else {
                    Assert.assertFalse("No error " + messageTail, expectedError);
                }
            }
            true
        })
    }

    private val expectedErrors = setOf(
            "testData/ctrs/test/1.1.0/run-pass/utf8-bom.rs",
            "testData/ctrs/test/1.1.0/run-pass/macro-interpolation.rs",
            "testData/ctrs/test/1.1.0/run-pass/small-enums-with-fields.rs"
    )
}


