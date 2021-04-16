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
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.RsReferenceElementBase
import org.rust.lang.core.psi.ext.contextualFile

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
        mark: TestmarkPred,
        resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile
    ) = mark.checkHit { stubOnlyResolve(code, resolveFileProducer) }

    protected fun stubOnlyResolve(
        @Language("Rust") code: String,
        resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile
    ) = stubOnlyResolve<RsReferenceElement>(fileTreeFromText(code), resolveFileProducer)

    protected inline fun <reified T : PsiElement> stubOnlyResolve(
        fileTree: FileTree,
        noinline resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile,
        noinline customCheck: (PsiElement) -> Unit = {}
    ) = resolveByFileTree(T::class.java, fileTree, { testProject ->
        checkAstNotLoaded { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        }
    }, resolveFileProducer, customCheck)

    protected inline fun <reified T : PsiElement> resolveByFileTree(
        fileTree: FileTree,
        noinline resolveFileProducer: (PsiElement) -> VirtualFile = this::getActualResolveFile,
        noinline customCheck: (PsiElement) -> Unit = {}
    ) = resolveByFileTree(T::class.java, fileTree, {}, resolveFileProducer, customCheck)

    protected fun <T : PsiElement> resolveByFileTree(
        referenceClass: Class<T>,
        fileTree: FileTree,
        configure: (TestProject) -> Unit,
        resolveFileProducer: (PsiElement) -> VirtualFile,
        customCheck: (PsiElement) -> Unit
    ) {
        val testProject = fileTree.createAndOpenFileWithCaretMarker()

        configure(testProject)

        val (referenceElement, resolveVariants, offset) = findElementWithDataAndOffsetInEditor(referenceClass, "^")

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
