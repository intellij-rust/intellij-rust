package org.rust.lang.core

import org.rust.lang.RustTestCase
import org.rust.lang.core.psi.RustPatIdent

class RustResolveTestCase : RustTestCase() {
    override fun getTestDataPath() = "testData/resolve"
    private fun referenceAtCaret() = file.findReferenceAt(myFixture.caretOffset)!!

    fun testArgument() {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()!!

        assertInstanceOf(declaration, RustPatIdent::class.java)
        assertEquals(declaration.text, usage.canonicalText)
    }

    fun testUnbound() {
        myFixture.configureByFile(fileName)

        val usage = referenceAtCaret()
        val declaration = usage.resolve()

        assertNull(declaration)
    }
}