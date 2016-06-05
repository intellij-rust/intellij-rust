package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.indexes.RustModulesIndex
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustResolveTestCaseBase : RustTestCaseBase() {

    override val dataPath = "org/rust/lang/core/resolve/fixtures"

    private fun assertIsValidDeclaration(declaration: PsiElement, usage: RustReference,
                                         expectedOffset: Int?) {

        assertThat(declaration).isInstanceOf(RustNamedElement::class.java)
        declaration as RustNamedElement


        if (expectedOffset != null) {
            assertThat(declaration.textOffset).isEqualTo(expectedOffset)
        } else {
            assertThat(declaration.name).isEqualTo(usage.canonicalText)
        }
    }

    override fun setUp() {
        super.setUp()

        // Invalidate cache for every run
        FileBasedIndex
            .getInstance()
            .requestRebuild(RustModulesIndex.ID)
    }

    protected fun checkIsBound(atOffset: Int? = null) {
        val usage = getReference()
        assertThat(usage.resolve())
            .withFailMessage("Failed to resolve `${usage.element.text}`.")
            .isNotNull()
        val declaration = usage.resolve()!!

        assertIsValidDeclaration(declaration, usage, atOffset)
    }

    protected fun checkIsUnbound() {
        val declaration = getReference().resolve()
        assertThat(declaration).isNull()
    }

    private fun getReference(): RustReference {
        return requireNotNull(myFixture.getReferenceAtCaretPosition(fileName)) {
            "No reference at caret in `$fileName`"
        } as RustReference
    }
}
