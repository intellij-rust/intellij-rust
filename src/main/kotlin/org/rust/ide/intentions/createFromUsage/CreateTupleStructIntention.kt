/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.lints.isCamelCase
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

class CreateTupleStructIntention : RsElementBaseIntentionAction<CreateTupleStructIntention.Context>() {
    override fun getFamilyName() = "Create tuple struct"

    class Context(val name: String, val call: RsCallExpr, val target: RsMod)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>()
        val functionCall = path?.parentOfType<RsCallExpr>()
        if (functionCall != null) {
            if (!functionCall.expr.isAncestorOf(path)) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val target = getTargetItemForFunctionCall(path) ?: return null
            if (target !is CallableInsertionTarget.Module) return null

            val name = path.referenceName ?: return null
            if (!name.isCamelCase()) return null

            val expectedType = functionCall.expectedType ?: TyUnknown
            // Do not offer the intention if the expected type is known
            if (expectedType !is TyUnknown) return null

            text = "Create tuple struct `$name`"
            return Context(name, functionCall, target.module)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val struct = buildStruct(project, ctx) ?: return
        val containingFunction = ctx.call.parentOfType<RsFunction>() ?: return
        val inserted = insertStruct(ctx.target, struct, containingFunction)

        val types = ctx.call.valueArgumentList.exprList.map { it.type }
        RsImportHelper.importTypeReferencesFromTys(inserted, types)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val fields = inserted.tupleFields?.tupleFieldDeclList.orEmpty()
        if (inserted.containingFile == ctx.call.containingFile && fields.isNotEmpty()) {
            val unknownTypes = inserted.descendantsOfType<RsInferType>()
            if (unknownTypes.isNotEmpty()) {
                editor.buildAndRunTemplate(inserted, unknownTypes.map { it.createSmartPointer() })
            } else {
                editor.caretModel.moveToOffset(fields[0].textOffset)
            }
        } else {
            inserted.navigate(true)
        }
    }

    private fun buildStruct(project: Project, ctx: Context): RsStructItem? {
        val factory = RsPsiFactory(project)
        val visibility = getVisibility(ctx.target, ctx.call.containingMod)
        val fields = ctx.call.valueArgumentList.exprList.joinToString(separator = ", ") {
            "$visibility${it.type.renderInsertionSafe(includeLifetimeArguments = true)}"
        }
        return factory.tryCreateStruct("${visibility}struct ${ctx.name}($fields);")
    }
}
