/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.rust.RsBundle
import org.rust.lang.core.psi.ext.endOffset
import org.rust.toml.isCargoToml
import org.rust.toml.isDependencyListHeader
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlValue

class SimplifyDependencySpecificationIntention : RsTomlElementBaseIntentionAction<SimplifyDependencySpecificationIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.simplify.dependency.specification")
    override fun getFamilyName(): String = text

    override fun findApplicableContextInternal(project: Project, editor: Editor, element: PsiElement): Context? {
        if (!element.containingFile.isCargoToml) return null

        val dependency = element.parentsOfType<TomlKeyValue>()
            .firstOrNull { (it.parent as? TomlTable)?.header?.isDependencyListHeader == true } ?: return null

        val dependencyValue = dependency.value as? TomlInlineTable ?: return null
        val version = dependencyValue.entries.singleOrNull().takeIf {
            it?.key?.segments?.singleOrNull()?.text == "version"
        }?.value ?: return null

        return Context(dependencyValue, version)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val offset = ctx.value.replace(ctx.version.copy())?.endOffset ?: return
        editor.caretModel.moveToOffset(offset)
    }

    class Context(val value: TomlInlineTable, val version: TomlValue)
}
