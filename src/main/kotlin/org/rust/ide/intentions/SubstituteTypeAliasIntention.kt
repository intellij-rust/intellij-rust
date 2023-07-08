/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.ide.refactoring.inlineTypeAlias.RsInlineTypeAliasProcessor
import org.rust.ide.refactoring.inlineTypeAlias.fillPathWithActualType
import org.rust.ide.refactoring.inlineTypeAlias.tryGetTypeAliasSubstitutionUsingParent
import org.rust.ide.utils.PsiModificationUtil
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.resolve.ref.advancedResolveTypeAliasToImpl
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.rawType

/** See also [RsInlineTypeAliasProcessor] */
class SubstituteTypeAliasIntention : RsElementBaseIntentionAction<SubstituteTypeAliasIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.substitute.type.alias")
    override fun getFamilyName() = text

    data class Context(
        val path: RsPath,
        val typeAlias: RsTypeAlias,
        val typeAliasReference: RsTypeReference,
        val substitution: Substitution
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>() ?: return null
        val target = path.reference?.advancedResolveTypeAliasToImpl() ?: return null
        val typeAlias = target.element as? RsTypeAlias ?: return null
        val typeRef = typeAlias.typeReference ?: return null

        if (!PsiModificationUtil.canReplace(path)) return null

        return Context(path, typeAlias, typeRef, target.subst)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val substitution = tryGetTypeAliasSubstitutionUsingParent(ctx.path, ctx.typeAlias) ?: ctx.substitution
        val inlined = fillPathWithActualType(ctx.path, ctx.typeAliasReference, substitution) ?: return
        RsImportHelper.importTypeReferencesFromTy(inlined, ctx.typeAliasReference.rawType)
    }
}
