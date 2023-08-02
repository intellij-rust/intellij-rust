/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.refactoring.RsPromoteModuleToDirectoryAction
import org.rust.lang.RsConstants.MOD_RS_FILE
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem

/**
 * Creates module file by the given module declaration.
 */
class AddModuleFileFix(
    modDecl: RsModDeclItem,
    private val expandModuleFirst: Boolean,
    private val location: Location = Location.File
) : RsQuickFixBase<RsModDeclItem>(modDecl) {
    @IntentionName
    private val text = RsBundle.message("intention.name.create.module.file", modDecl.path)
    override fun getText() = text

    override fun getFamilyName() = RsBundle.message("intention.family.name.create.module.file")

    // No intention preview because it creates new file
    override fun getFileModifierForPreview(target: PsiFile): FileModifier? = null

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

    override fun invoke(project: Project, editor: Editor?, element: RsModDeclItem) {
        if (expandModuleFirst) {
            val containingFile = element.containingFile as RsFile
            RsPromoteModuleToDirectoryAction.expandModule(containingFile)
        }

        val existing = element.reference.resolve()?.containingFile
        if (existing != null) {
            existing.navigate(true)
            return
        }

        val dir = element.containingMod.getOwnedDirectory(createIfNotExists = true) ?: return
        when (location) {
            Location.File -> dir.createFile("${element.name}.rs")
            Location.Directory -> dir.getOrCreateSubdirectory("${element.name}").createFile(MOD_RS_FILE)
        }.navigate(true)
    }

    companion object {
        fun createFixes(modDecl: RsModDeclItem, expandModuleFirst: Boolean): List<AddModuleFileFix> = listOf(
            AddModuleFileFix(modDecl, expandModuleFirst, Location.File),
            AddModuleFileFix(modDecl, expandModuleFirst, Location.Directory)
        )
    }
}

private fun PsiDirectory.getOrCreateSubdirectory(name: String) = findSubdirectory(name) ?: createSubdirectory(name)
