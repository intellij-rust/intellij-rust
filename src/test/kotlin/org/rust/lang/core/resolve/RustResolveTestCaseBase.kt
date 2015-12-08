package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustResolveTestCaseBase : RustTestCase() {
    override fun getTestDataPath() = "src/test/resources/org/rust/lang/core/resolve/fixtures"

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

    final protected fun checkIsBound(atOffset: Int? = null) {
        val usage = myFixture.getReferenceAtCaretPosition(fileName) as RustReference
        assertThat(usage.resolve())
                .overridingErrorMessage("Failed to resolve `${usage.element.text}`.")
                .isNotNull()
        val declaration = usage.resolve()!!

        assertIsValidDeclaration(declaration, usage, atOffset)
    }

    final protected fun checkIsUnbound() {
        val usage = myFixture.getReferenceAtCaretPosition(fileName) as RustReference
        val declaration = usage.resolve()

        assertThat(declaration).isNull()
    }
}
