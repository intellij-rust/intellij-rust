/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.inspections.lints.isCamelCase
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.ide.utils.template.canRunTemplateFor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.moveCaretToOffset

class CreateTupleStructIntention : RsElementBaseIntentionAction<CreateTupleStructIntention.Context>() {
    override fun getFamilyName() = RsBundle.message("intention.family.name.create.tuple.struct")

    class Context(
        val name: String,
        val call: RsCallExpr,
        val targetMod: RsMod,
        val place: PsiInsertionPlace
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.contextStrict<RsPath>()
        val functionCall = path?.contextStrict<RsCallExpr>()
        if (functionCall != null) {
            if (!functionCall.expr.isContextOf(path)) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val targetMod = getWritablePathMod(path) ?: return null

            val name = path.referenceName ?: return null
            if (!name.isCamelCase()) return null

            val expectedType = functionCall.expectedType ?: TyUnknown
            // Do not offer the intention if the expected type is known
            if (expectedType !is TyUnknown) return null
            val place = PsiInsertionPlace.forItemInModBefore(targetMod, functionCall) ?: return null

            text = RsBundle.message("intention.name.create.tuple.struct", name)
            return Context(name, functionCall, targetMod, place)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newStruct = buildStruct(project, ctx) ?: return
        val inserted = ctx.place.insert(newStruct)

        val types = ctx.call.valueArgumentList.exprList.map { it.type }
        RsImportHelper.importTypeReferencesFromTys(inserted, types)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val fields = inserted.tupleFields?.tupleFieldDeclList.orEmpty()
        if (editor.canRunTemplateFor(inserted) && fields.isNotEmpty()) {
            val unknownTypes = inserted.descendantsOfType<RsInferType>()
            if (unknownTypes.isNotEmpty()) {
                editor.buildAndRunTemplate(inserted, unknownTypes)
            } else {
                editor.moveCaretToOffset(fields[0], fields[0].textOffset)
            }
        } else {
            inserted.navigate(true)
        }
    }

    private fun buildStruct(project: Project, ctx: Context): RsStructItem? {
        val factory = RsPsiFactory(project)
        val visibility = getVisibility(ctx.targetMod, ctx.call.containingMod)
        val fields = generateFields(ctx.call, visibility)
        return factory.tryCreateStruct("${visibility}struct ${ctx.name}$fields;")
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    companion object {
        fun generateFields(call: RsCallExpr, visibility: String): String =
            call.valueArgumentList.exprList.joinToString(separator = ", ", prefix = "(", postfix = ")") {
                "$visibility${it.type.renderInsertionSafe(includeLifetimeArguments = true)}"
            }
    }
}
