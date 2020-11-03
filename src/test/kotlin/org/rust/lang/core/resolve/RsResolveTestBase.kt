/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapiext.TestmarkPred
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsResolveTestBase : RsTestBase() {
    protected open fun checkByCode(@Language("Rust") code: String) =
        checkByCode(code, "main.rs")

    protected open fun checkByCode(@Language("Rust") code: String, fileName: String) =
        checkByCodeGeneric<RsNamedElement>(code, fileName)

    protected inline fun <reified T : PsiElement> checkByCodeGeneric(
        @Language("Rust") code: String,
        fileName: String = "main.rs"
    ) = checkByCodeGeneric2<RsReferenceElementBase, T>(code, fileName)

    protected inline fun <reified R : PsiElement, reified T : PsiElement> checkByCodeGeneric2(
        code: String,
        fileName: String
    ) = checkByCodeGeneric2(R::class.java, T::class.java, code, fileName)

    protected fun <R : PsiElement, T : PsiElement> checkByCodeGeneric2(
        referenceClass: Class<R>,
        targetPsiClass: Class<T>,
        @Language("Rust") code: String,
        fileName: String = "main.rs"
    ) {
        InlineFile(code, fileName)

        val (refElement, data, offset) = findElementWithDataAndOffsetInEditor(referenceClass, "^")

        if (data == "unresolved") {
            val resolved = refElement.reference?.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = refElement.checkedResolve(offset)
        val target = findElementInEditor(targetPsiClass, "X")

        check(resolved == target) {
            "$refElement `${refElement.text}` should resolve to $target (${target.text}), was $resolved (${resolved.text}) instead"
        }
    }

    protected fun checkByCode(@Language("Rust") code: String, mark: TestmarkPred) =
        mark.checkHit { checkByCode(code) }

    protected fun stubOnlyResolve(
        @Language("Rust") code: String,
        resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile
    ) = stubOnlyResolve<RsReferenceElement>(fileTreeFromText(code), resolveFileProducer)

    protected fun stubOnlyResolve(
        @Language("Rust") code: String,
        mark: TestmarkPred,
        resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile
    ) = mark.checkHit { stubOnlyResolve(code, resolveFileProducer) }

    protected inline fun <reified T : PsiElement> stubOnlyResolve(
        fileTree: FileTree,
        resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile,
        customCheck: (PsiElement) -> Unit = {}
    ) {
        val testProject = fileTree.createAndOpenFileWithCaretMarker()

        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })

        val (referenceElement, resolveVariants, offset) = findElementWithDataAndOffsetInEditor<T>()

        if (resolveVariants == "unresolved") {
            val element = referenceElement.findReference(offset)?.resolve()
            if (element != null) {
                // Turn off virtual file filter to show element text
                // because it requires access to virtual file
                checkAstNotLoaded(VirtualFileFilter.NONE)
                error("Should not resolve ${referenceElement.text} to ${element.text}")
            }
            return
        }

        val element = referenceElement.checkedResolve(offset)
        customCheck(element)
        val actualResolveFile = resolveFileProducer(element)

        val resolveFiles = resolveVariants.split("|")
        if (resolveFiles.size == 1) {
            val result = check(actualResolveFile, resolveFiles.single())
            if (result is ResolveResult.Err) {
                error(result.message)
            }
        } else {
            if (resolveFiles.none { check(actualResolveFile, it) == ResolveResult.Ok }) {
                error("Should resolve to any of $resolveFiles, was ${actualResolveFile.path} instead")
            }
        }
    }

    protected fun getActualResolveFile(element: PsiElement): VirtualFile {
        return if (element is PsiDirectory) element.virtualFile else element.contextualFile.virtualFile
    }

    protected fun check(actualResolveFile: VirtualFile, expectedFilePath: String): ResolveResult {
        return checkResolvedFile(actualResolveFile, expectedFilePath) { path -> myFixture.findFileInTempDir(path) }
    }
}

fun PsiElement.findReference(offset: Int): PsiReference? = findReferenceAt(offset - startOffset)

fun PsiElement.checkedResolve(offset: Int): PsiElement {
    val reference = findReference(offset) ?: error("element doesn't have reference")
    val resolved = reference.resolve() ?: run {
        val multiResolve = (reference as? RsReference)?.multiResolve().orEmpty()
        check(multiResolve.size != 1)
        if (multiResolve.isEmpty()) {
            error("Failed to resolve $text")
        } else {
            error("Failed to resolve $text, multiple variants:\n${multiResolve.joinToString()}")
        }
    }

    check(reference.isReferenceTo(resolved)) {
        "Incorrect `isReferenceTo` implementation in `${reference.javaClass.name}`"
    }

    checkSearchScope(this, resolved)

    return resolved
}

private fun checkSearchScope(referenceElement: PsiElement, resolvedTo: PsiElement) {
    if (resolvedTo.isExpandedFromMacro) return
    val virtualFile = referenceElement.containingFile.virtualFile ?: return
    check(resolvedTo.useScope.contains(virtualFile)) {
        "Incorrect `getUseScope` implementation in `${resolvedTo.javaClass.name}`;" +
            "also this can means that `pub` visibility is missed somewhere in the test"
    }
}
