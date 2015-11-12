package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.ref.RustReference

class RustResolveTestCase : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/core/resolve/fixtures"

    fun testFunctionArgument() = checkIsBound()
    fun testLocals() = checkIsBound(atOffset = 19)
    fun testShadowing() = checkIsBound(atOffset = 35)
    fun testNestedPatterns() = checkIsBound()
    fun testClosure() = checkIsBound()
    fun testMatch() = checkIsBound()
    fun testIfLet() = checkIsBound()
    fun testIfLetX() = checkIsUnbound()
    fun testWhileLet() = checkIsBound()
    fun testWhileLetX() = checkIsUnbound()
    fun testFor() = checkIsBound()
    fun testTraitMethodArgument() = checkIsBound()
    fun testImplMethodArgument() = checkIsBound()
    fun testStructPatterns1() = checkIsBound(atOffset = 69)
    fun testStructPatterns2() = checkIsBound()
    fun testModItems() = checkIsBound()
    fun testCrateItems() = checkIsBound()
    fun testNestedModule() = checkIsBound(atOffset = 48)
    fun testUnbound() = checkIsUnbound()
    fun testOrdering() = checkIsUnbound()
    fun testModBoundary() = checkIsUnbound()

    private fun assertIsValidDeclaration(declaration: PsiElement, usage: RustReference,
                                         expectedOffset: Int?) {

        assertThat(declaration).isInstanceOf(RustNamedElement::class.java)
        declaration as RustNamedElement


        if (expectedOffset != null) {
            assertThat(declaration.textOffset).isEqualTo(expectedOffset)
        } else {
            assertThat(declaration.name).isEqualTo(usage.element.name)
        }
    }

    private fun checkIsBound(atOffset: Int? = null) {
        val usage = myFixture.getReferenceAtCaretPosition(fileName) as RustReference
        val declaration = usage.resolve()!!

        assertIsValidDeclaration(declaration, usage, atOffset)
    }

    private fun checkIsUnbound() {
        val usage = myFixture.getReferenceAtCaretPosition(fileName) as RustReference
        val declaration = usage.resolve()

        assertThat(declaration).isNull()
    }
}
