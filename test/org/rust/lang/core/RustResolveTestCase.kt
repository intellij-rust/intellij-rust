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
    fun testUnbound()              { doTestUnbound() }
    //@formatter:on

    private fun assertIsValidDeclaration(declaration: PsiElement, usage: PsiReference) {
        assertInstanceOf(declaration, RustPatIdent::class.java)
        assertEquals(declaration.text, usage.canonicalText)
    }

    private fun doTestBound() {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()!!

        assertIsValidDeclaration(declaration, usage)
    }

    private fun doTestUnbound() {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()

        assertNull(declaration)
    }
}
