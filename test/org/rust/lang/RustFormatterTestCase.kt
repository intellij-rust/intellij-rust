package org.rust.lang

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager

class RustFormatterTestCase : RustTestCase() {
    override fun getTestDataPath() = "testData/formatter"

    private fun doTest() {
        myFixture.configureByFile(fileName)

        object : WriteCommandAction.Simple<Any>(project) {
            override fun run() {
                CodeStyleManager.getInstance(project).reformat(myFixture.file)
            }
        }.execute()

        myFixture.checkResultByFile(goldFileName)
    }

    //@formatter:off
    fun testBlocks()                { doTest() }
    fun testItems()                 { doTest() }
    //@formatter:on
}
