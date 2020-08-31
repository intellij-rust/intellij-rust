/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.endOffsetInParent
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.types.type

class SubstituteAssociatedTypeIntention : RsElementBaseIntentionAction<SubstituteAssociatedTypeIntention.Context>() {
    override fun getText() = "Substitute associated type"
    override fun getFamilyName() = text

    data class Context(val path: RsPath,
                       val typeAliasReference: RsTypeReference)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>() ?: return null
        val typeAlias = path.reference?.resolve() as? RsTypeAlias ?: return null
        if (!typeAlias.owner.isImplOrTrait) return null
        val type = typeAlias.typeReference ?: return null
        return Context(path, type)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val typeRef = ctx.typeAliasReference
        val isTypeContext = ctx.path.parentOfType<RsTypeReference>() != null
        val createdPath = factory.tryCreatePath(typeRef.type.renderInsertionSafe(), RustParserUtil.PathParsingMode.TYPE)
            ?: return

        // S<u32> -> S::<u32> in expression context
        val insertedPath: RsPath = if (!isTypeContext && createdPath.typeArgumentList != null) {
            val end = createdPath.identifier?.endOffsetInParent ?: 0
            val pathText = createdPath.text
            val newPath = pathText.substring(0, end) + "::" + pathText.substring(end)
            val path = factory.tryCreatePath(newPath, RustParserUtil.PathParsingMode.TYPE) ?: return
            ctx.path.replace(path) as RsPath
        } else {
            ctx.path.replace(createdPath) as RsPath
        }

        RsImportHelper.importTypeReferencesFromTy(insertedPath, typeRef.type)
    }
}
