/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.psi.PsiFileSystemItem
import org.rust.openapiext.checkWriteAccessAllowed

class RsDowngradeModuleToFile : com.intellij.refactoring.actions.BaseRefactoringAction() {
    override fun isEnabledOnElements(elements: Array<out com.intellij.psi.PsiElement>): Boolean = elements.all { it.isDirectoryMod }

    override fun getHandler(dataContext: com.intellij.openapi.actionSystem.DataContext): com.intellij.refactoring.RefactoringActionHandler = org.rust.lang.refactoring.RsDowngradeModuleToFile.Handler

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isAvailableForLanguage(language: com.intellij.lang.Language): Boolean = language.`is`(org.rust.lang.RsLanguage)

    private object Handler : com.intellij.refactoring.RefactoringActionHandler {
        override fun invoke(project: com.intellij.openapi.project.Project, editor: com.intellij.openapi.editor.Editor, file: com.intellij.psi.PsiFile, dataContext: com.intellij.openapi.actionSystem.DataContext?) {
            org.rust.lang.refactoring.RsDowngradeModuleToFile.Handler.invoke(project, arrayOf(file), dataContext)
        }

        override fun invoke(project: com.intellij.openapi.project.Project, elements: Array<out com.intellij.psi.PsiElement>, dataContext: com.intellij.openapi.actionSystem.DataContext?) {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                for (element in elements) {
                    org.rust.lang.refactoring.contractModule(element as PsiFileSystemItem)
                }
            }
        }
    }
}

private fun contractModule(fileOrDirectory: com.intellij.psi.PsiFileSystemItem) {
    checkWriteAccessAllowed()

    val (file, dir) = when (fileOrDirectory) {
        is org.rust.lang.core.psi.RsFile -> fileOrDirectory to fileOrDirectory.parent!!
        is com.intellij.psi.PsiDirectory -> fileOrDirectory.children.single() as org.rust.lang.core.psi.RsFile to fileOrDirectory
        else -> error("Can contract only files and directories")
    }

    val dst = dir.parent!!
    val fileName = "${dir.name}.rs"
    com.intellij.psi.impl.file.PsiFileImplUtil.setName(file, fileName)
    com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil.doMoveFile(file, dst)
    dir.delete()
}

private val com.intellij.psi.PsiElement.isDirectoryMod: Boolean get() {
    return when (this) {
        is org.rust.lang.core.psi.RsFile -> name == org.rust.lang.core.psi.ext.RsMod.Companion.MOD_RS && containingDirectory?.children?.size == 1
        is com.intellij.psi.PsiDirectory -> {
            val child = children.singleOrNull()
            child is org.rust.lang.core.psi.RsFile && child.name == org.rust.lang.core.psi.ext.RsMod.Companion.MOD_RS
        }
        else -> false
    }
}
