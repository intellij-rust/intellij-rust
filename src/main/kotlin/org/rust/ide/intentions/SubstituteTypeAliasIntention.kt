/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.presentation.getStubOnlyText
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.endOffsetInParent
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

class SubstituteTypeAliasIntention : RsElementBaseIntentionAction<SubstituteTypeAliasIntention.Context>() {
    override fun getText() = "Substitute type alias"
    override fun getFamilyName() = text

    data class Context(
        val path: RsPath,
        val typeAlias: RsTypeAlias,
        val typeAliasReference: RsTypeReference,
        val substitution: Substitution
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>() ?: return null
        val target = path.reference?.advancedResolve() ?: return null

        val typeAlias = target.element as? RsTypeAlias ?: return null

        val typeRef = typeAlias.typeReference ?: return null
        return Context(path, typeAlias, typeRef, target.subst)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val typeRef = ctx.typeAliasReference
        val isTypeContext = ctx.path.parentOfType<RsTypeReference>() != null

        val parentPath = ctx.path.parent as? RsPath
        val selfTy = if (parentPath != null && parentPath.parent is RsPathExpr) {
            parentPath.reference?.advancedResolve()?.subst?.let { it[TyTypeParameter.self()] }
        } else {
            null
        }
        val subst = if (selfTy != null) {
            val (lookup, items) = ctx.path.implLookupAndKnownItems
            val inf = RsInferenceContext(project, lookup, items)
            val subst = inf.instantiateBounds(ctx.typeAlias, selfTy)
            val type = typeRef.type.substitute(subst)
            inf.combineTypes(type, selfTy)
            subst.mapTypeValues { (_, v) -> inf.resolveTypeVarsIfPossible(v) }
        } else {
            ctx.substitution
        }

        val renderedType = typeRef.getStubOnlyText(subst)
        val createdPath = factory.tryCreatePath(renderedType, RustParserUtil.PathParsingMode.TYPE)
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
