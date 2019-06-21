/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.refactoring.RsPromoteModuleToDirectoryAction
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem

/**
 * Creates module file by the given module declaration.
 */
class AddModuleFileFix(
    modDecl: RsModDeclItem,
    private val expandModuleFirst: Boolean,
    private val location: Location = Location.File
) : LocalQuickFixAndIntentionActionOnPsiElement(modDecl) {
    private val text = "Create module file `${modDecl.path}`"
    override fun getText() = text

    override fun getFamilyName() = "Create module file"

    enum class Location {
        /**
         * Creates a module in the same directory. E.g. `mod foo` creates a file `foo.rs`
         */
        File,
        /**
         * Creates a module in a subdirectory. E.g. `mod foo` creates a file `foo/mod.rs`
         */
        Directory
    }

    private val RsModDeclItem.path: String
        get() = when (location) {
            Location.File -> "$name.rs"
            Location.Directory -> "$name/mod.rs"
        }

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val modDecl = startElement as RsModDeclItem
        if (expandModuleFirst) {
            val containingFile = modDecl.containingFile as RsFile
            RsPromoteModuleToDirectoryAction.expandModule(containingFile)
        }

        val existing = modDecl.reference.resolve()?.containingFile
        if (existing != null) {
            existing.navigate(true)
            return
        }

        val dir = modDecl.containingMod.getOwnedDirectory(createIfNotExists = true) ?: return
        when (location) {
            Location.File -> dir.createFile("${modDecl.name}.rs")
            Location.Directory -> dir.getOrCreateSubdirectory("${modDecl.name}").createFile("mod.rs")
        }.navigate(true)
    }

    companion object {
        fun createFixes(modDecl: RsModDeclItem, expandModuleFirst: Boolean) = listOf(
            AddModuleFileFix(modDecl, expandModuleFirst, Location.File),
            AddModuleFileFix(modDecl, expandModuleFirst, Location.Directory)
        )
    }
}

private fun PsiDirectory.getOrCreateSubdirectory(name: String) = findSubdirectory(name) ?: createSubdirectory(name)
