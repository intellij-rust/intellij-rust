/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import org.rust.cargo.CargoConstants
import org.rust.lang.core.psi.ext.endOffset
import org.rust.toml.isDependencyListHeader
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable

class ExtractDependencySpecificationIntention : RsTomlElementBaseIntentionAction<TomlKeyValue>() {
    override fun getText() = "Extract dependency specification"
    override fun getFamilyName(): String = text

    override fun findApplicableContextInternal(project: Project, editor: Editor, element: PsiElement): TomlKeyValue? {
        if (element.containingFile.name != CargoConstants.MANIFEST_FILE) return null

        val dependencyTable = element.parentOfType<TomlTable>()
            ?.takeIf { it.header.isDependencyListHeader }
            ?: return null

        val dependency = element.parentsOfType<TomlKeyValue>()
            .firstOrNull { it.parent == dependencyTable }

        return dependency?.takeIf { it.value is TomlInlineTable }
    }

    override fun invoke(project: Project, editor: Editor, ctx: TomlKeyValue) {
        val inlineTable = ctx.value as? TomlInlineTable ?: return
        val table = ctx.parent as? TomlTable ?: return
        val dependencyTableName = table.header.key?.text.orEmpty()
        val crateName = ctx.key.text
        val newTable = TomlPsiFactory(project).createTable("$dependencyTableName.$crateName")

        val psiParserFacade = PsiParserFacade.SERVICE.getInstance(project)
        for (keyValue in inlineTable.entries) {
            newTable.add(psiParserFacade.createWhiteSpaceFromText("\n"))
            newTable.add(keyValue)
        }

        val newTableOffset = if (table.entries.size == 1) {
            table.replace(newTable).endOffset
        } else {
            ctx.delete()
            val whitespace = psiParserFacade.createWhiteSpaceFromText("\n\n")

            val addedTable = table.parent.addAfter(newTable, table)
            table.parent.addAfter(whitespace, table)
            addedTable.endOffset
        }
        editor.caretModel.moveToOffset(newTableOffset)
    }
}
