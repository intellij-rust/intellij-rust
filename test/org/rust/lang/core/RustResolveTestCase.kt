package org.rust.lang.core

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.RustTestCase
import org.rust.lang.core.psi.RustPatIdent

class RustResolveTestCase : RustTestCase() {
    override fun getTestDataPath() = "testData/resolve"
    private fun referenceAtCaret() = file.findReferenceAt(myFixture.caretOffset)!!

    //@formatter:off
    fun testArgument()             { doTestBound()   }
    fun testLocals()               { doTestBound()   }
    fun testShadowing()            { doTestBound(35) }
    fun testUnbound()              { doTestUnbound() }
    fun testOrdering()             { doTestUnbound() }
    //@formatter:on

    private fun assertIsValidDeclaration(declaration: PsiElement, usage: PsiReference,
                                         expectedOffset: Int?) {

        assertInstanceOf(declaration, RustPatIdent::class.java)
        assertEquals(usage.canonicalText, declaration.text)
        if (expectedOffset != null) {
            assertEquals(expectedOffset, declaration.textRange.startOffset)
        }
    }

    private fun doTestBound(expectedOffset: Int? = null) {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()!!

        assertIsValidDeclaration(declaration, usage, expectedOffset)
    }

    private fun doTestUnbound() {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()

        assertNull(declaration)
    }
}
