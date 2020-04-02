/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.containers.map2Array
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.openapiext.toPsiDirectory
import org.rust.openapiext.toPsiFile

abstract class RsMoveFileTestBase : RsTestBase() {

    private fun performMove(
        rootDirectory: VirtualFile,
        elementsToMove: Array<String>,
        targetDirectory: String,
        searchForReferences: Boolean = true
    ) {
        val psiElementsToMove: Array<PsiElement> = elementsToMove.map2Array {
            val virtualFile = rootDirectory.findFileByRelativePath(it)!!
            if (virtualFile.isDirectory) virtualFile.toPsiDirectory(project)!! else virtualFile.toPsiFile(project)!!
        }

        if (targetDirectory != ".") {
            runWriteAction { VfsUtil.createDirectoryIfMissing(rootDirectory, targetDirectory) }
        }
        val psiTargetDirectory = rootDirectory.findFileByRelativePath(targetDirectory)!!.toPsiDirectory(project)!!
        RsMoveFilesOrDirectoriesDialog(project, psiElementsToMove, null, null)
            .doPerformMove(psiTargetDirectory, searchForReferences, EmptyRunnable.INSTANCE)
    }

    protected fun doTest(elementsToMove: Array<String>, targetDirectory: String) = checkByDirectory { rootDirectory ->
        performMove(rootDirectory, elementsToMove, targetDirectory)
    }

    // elementToMove - path to file or directory
    protected fun doTest(elementToMove: String, targetDirectory: String) =
        doTest(arrayOf(elementToMove), targetDirectory)

    protected fun doTest(
        elementsToMove: Array<String>,
        targetDirectory: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        searchForReferences: Boolean = true
    ) = checkByDirectory(before, after) { testProject ->
        performMove(testProject.root, elementsToMove, targetDirectory, searchForReferences)
    }

    protected fun doTest(
        elementToMove: String,
        targetDirectory: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTest(arrayOf(elementToMove), targetDirectory, before, after)

    protected fun doTestExpectError(
        elementsToMove: Array<String>,
        targetDirectory: String,
        @Language("Rust") source: String
    ) = checkByDirectory(source, source) { testProject ->
        performMove(testProject.root, elementsToMove, targetDirectory)
    }
}
