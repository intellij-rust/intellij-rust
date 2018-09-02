/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.Testmark

abstract class RsResolveTestBase : RsTestBase() {
    protected open fun checkByCode(@Language("Rust") code: String) {
        InlineFile(code)

        val (refElement, data, offset) = findElementWithDataAndOffsetInEditor<RsWeakReferenceElement>("^")

        if (data == "unresolved") {
            val resolved = refElement.reference?.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = refElement.checkedResolve(offset)
        val target = findElementInEditor<RsNamedElement>("X")

        check(resolved == target) {
            "$refElement `${refElement.text}` should resolve to $target, was $resolved instead"
        }
    }

    protected fun checkByCode(@Language("Rust") code: String, mark: Testmark) =
        mark.checkHit { checkByCode(code) }

    protected fun stubOnlyResolve(@Language("Rust") code: String) {
        stubOnlyResolve<RsWeakReferenceElement>(fileTreeFromText(code))
    }

    protected fun stubOnlyResolve(@Language("Rust") code: String, mark: Testmark) =
        mark.checkHit { stubOnlyResolve(code) }

    protected inline fun <reified T: PsiElement> stubOnlyResolve(fileTree: FileTree, customCheck: (PsiElement) -> Unit = {}) {
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
        val actualResolveFile = element.containingFile.virtualFile

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

    protected fun check(actualResolveFile: VirtualFile, expectedFilePath: String): ResolveResult {
        if (expectedFilePath.startsWith("...")) {
            if (!actualResolveFile.path.endsWith(expectedFilePath.drop(3))) {
                return ResolveResult.Err("Should resolve to $expectedFilePath, was ${actualResolveFile.path} instead")

            }
        } else {
            val expectedResolveFile = myFixture.findFileInTempDir(expectedFilePath)
                ?: return ResolveResult.Err("Can't find `$expectedFilePath` file")

            if (actualResolveFile != expectedResolveFile) {
                return ResolveResult.Err("Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead")
            }
        }
        return ResolveResult.Ok
    }

    protected sealed class ResolveResult {
        object Ok : ResolveResult()
        data class Err(val message: String) : ResolveResult()
    }
}

fun PsiElement.findReference(offset: Int): PsiReference? = findReferenceAt(offset - textRange.startOffset)

fun PsiElement.checkedResolve(offset: Int): PsiElement {
    val reference = findReference(offset) ?: error("element doesn't have reference")
    return reference.resolve() ?: run {
        val multiResolve = (reference as? RsReference)?.multiResolve().orEmpty()
        check(multiResolve.size != 1)
        if (multiResolve.isEmpty()) {
            error("Failed to resolve $text")
        } else {
            error("Failed to resolve $text, multiple variants:\n${multiResolve.joinToString()}")
        }
    }
}
