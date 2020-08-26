/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.TestProject
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.document
import org.rust.openapiext.toPsiDirectory
import org.rust.openapiext.toPsiFile

abstract class RsMoveTopLevelItemsTestBase : RsTestBase() {

    protected fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkByDirectory(before.trimIndent(), after.trimIndent(), false, ::performMove)

    protected fun doTestConflictsError(@Language("Rust") before: String) =
        expect<BaseRefactoringProcessor.ConflictsInTestsException> {
            checkByDirectory(before.trimIndent(), "", true, ::performMove)
        }

    protected fun doTestNoConflicts(@Language("Rust") before: String) =
        checkByDirectory(before.trimIndent(), "", true, ::performMove)

    private fun performMove(testProject: TestProject) {
        val fileWithCaret = testProject.fileWithCaretOrSelection
        val sourceFile = myFixture.findFileInTempDir(fileWithCaret).toPsiFile(project)!!
        myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)

        val root = myFixture.findFileInTempDir(".").toPsiDirectory(project)!!
        val targetMod = searchElementInAllFiles(root.virtualFile) { it.getElementAtMarker(TARGET_MARKER) }
            ?.ancestorOrSelf<RsMod>()
            ?: error("Please add $TARGET_MARKER marker for target mod")
        sourceFile.putUserData(MOVE_TARGET_MOD_KEY, targetMod)

        myFixture.performEditorAction(IdeActions.ACTION_MOVE)
    }

    private fun <T> searchElementInAllFiles(root: VirtualFile, searcher: (PsiFile) -> T?): T? {
        var result: T? = null
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFileEx(file: VirtualFile): Result {
                val psiFile = file.toPsiFile(project) ?: return CONTINUE
                val resultCurrent = searcher(psiFile) ?: return CONTINUE
                result = resultCurrent
                return skipTo(root)
            }
        })
        return result
    }

    companion object {
        private const val TARGET_MARKER: String = "/*target*/"
    }
}

private fun PsiFile.getElementAtMarker(marker: String = "<caret>"): PsiElement? =
    getElementsAtMarker(marker).singleOrNull()

private fun PsiFile.getElementsAtMarker(marker: String = "<caret>"): List<PsiElement> =
    extractMultipleMarkerOffsets(project, marker).map {
        if (it == textLength) this else findElementAt(it)!!
    }

private fun PsiFile.extractMultipleMarkerOffsets(project: Project, marker: String): List<Int> =
    virtualFile.document!!.extractMultipleMarkerOffsets(project, marker)

private fun Document.extractMultipleMarkerOffsets(project: Project, marker: String): List<Int> {
    if (!text.contains(marker)) return emptyList()

    val offsets = mutableListOf<Int>()
    runWriteAction {
        val text = StringBuilder(text)
        while (true) {
            val offset = text.indexOf(marker)
            if (offset >= 0) {
                text.delete(offset, offset + marker.length)
                offsets += offset
            } else {
                break
            }
        }
        setText(text.toString())
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(this)
    return offsets
}
