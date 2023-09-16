/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.lang.core.psi.ext.endOffset
import org.rust.toml.isCargoToml
import org.rust.toml.isDependencyListHeader
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class ExpandDependencySpecificationIntention : RsTomlElementBaseIntentionAction<TomlKeyValue>() {
    override fun getText() = RsBundle.message("intention.name.expand.dependency.specification")
    override fun getFamilyName(): String = text

    override fun findApplicableContextInternal(project: Project, editor: Editor, element: PsiElement): TomlKeyValue? {
        if (!element.containingFile.isCargoToml) return null

        val keyValue = element.parentOfType<TomlKeyValue>() ?: return null
        val table = keyValue.parent as? TomlTable ?: return null
        if (!table.header.isDependencyListHeader) return null

        val value = keyValue.value
        if (value !is TomlLiteral || value.kind !is TomlLiteralKind.String) return null

        return keyValue
    }

    override fun invoke(project: Project, editor: Editor, ctx: TomlKeyValue) {
        val crateName = ctx.key.text
        val crateVersion = ctx.value?.text ?: "\"\""
        val newKeyValue = TomlPsiFactory(project).createKeyValue("$crateName = { version = $crateVersion }")
        val newKeyValueOffset = ctx.replace(newKeyValue).endOffset
        editor.caretModel.moveToOffset(newKeyValueOffset)
    }
}
