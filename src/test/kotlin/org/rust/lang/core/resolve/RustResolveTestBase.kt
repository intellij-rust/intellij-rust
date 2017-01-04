package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerImpl
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
        InlineFile(code)

        val (refElement, data) = findElementAndDataInEditor<RustReferenceElement>("^")

        if (data == "unresolved") {
            val resolved = refElement.reference.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = checkNotNull(refElement.reference.resolve()) {
            "Failed to resolve ${refElement.text}"
        }

        val target = findElementInEditor<RustNamedElement>("X")

        assertThat(resolved).isEqualTo(target)
    }

    protected fun stubOnlyResolve(@Language("Rust") code: String) {
        val fileSeparator = """^\s* //- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
        val fileNames = fileSeparator.findAll(code).map { it.groupValues[1] }.toList()
        val fileTexts = fileSeparator.split(code).filter(String::isNotBlank)

        check(fileNames.size == fileTexts.size) {
            "Have you placed `//- filename.rs` markers?"
        }
        for ((name, text) in fileNames.zip(fileTexts)) {
            myFixture.tempDirFixture.createFile(name, text)
        }
        (psiManager as PsiManagerImpl)
            .setAssertOnFileLoadingFilter(VirtualFileFilter { file ->
                !file.path.endsWith(fileNames[0])
            }, testRootDisposable)

        myFixture.configureFromTempProjectFile(fileNames[0])
        val (reference, resolveFile) = findElementAndDataInEditor<RustReferenceElement>()

        if (resolveFile == "unresolved") {
            val element = reference.reference.resolve()
            if (element != null) {
                error("Should not resolve ${reference.text} to ${element.text}")
            }
            return
        }

        val element = reference.reference.resolve()
            ?: error("Failed to resolve ${reference.text}")
        val actualResolveFile = element.containingFile.virtualFile

        if (resolveFile.startsWith("...")) {
            check(actualResolveFile.path.endsWith(resolveFile.drop(3))) {
                "Should resolve to $resolveFile, was ${actualResolveFile.path} instead"
            }
        } else {
            val expectedResolveFile = myFixture.findFileInTempDir(resolveFile)
                ?: error("Can't find `$resolveFile` file")

            check(actualResolveFile == expectedResolveFile) {
                "Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead"
            }
        }
    }

    private fun getReference(): RustReference {
        return requireNotNull(myFixture.getReferenceAtCaretPosition(fileName)) {
            "No reference at caret in `$fileName`"
        } as RustReference
    }
}
