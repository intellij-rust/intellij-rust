package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustReferenceElement
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustResolveTestBase : RustTestCaseBase() {

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

    protected fun checkByCode(@Language("Rust") code: String) {
        val file = InlineFile(code)

        val (refElement, data) = file.elementAndData<RustReferenceElement>("^")

        if (data == "unresolved") {
            assertThat(refElement.reference.resolve()).isNull()
            return
        }

        val resolved = checkNotNull(refElement.reference.resolve()) {
            "Failed to resolve ${refElement.text}"
        }

        val target = file.elementAtCaret<RustNamedElement>("X")

        assertThat(resolved).isEqualTo(target)
    }

    private fun getReference(): RustReference {
        return requireNotNull(myFixture.getReferenceAtCaretPosition(fileName)) {
            "No reference at caret in `$fileName`"
        } as RustReference
    }
}
