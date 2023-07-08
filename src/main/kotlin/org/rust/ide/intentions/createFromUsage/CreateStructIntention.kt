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
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.canRunTemplateFor
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

class CreateStructIntention : RsElementBaseIntentionAction<CreateStructIntention.Context>() {
    override fun getFamilyName() = RsBundle.message("intention.family.name.create.struct")

    class Context(
        val name: String,
        val structLiteral: RsStructLiteral,
        val targetMod: RsMod,
        val place: PsiInsertionPlace,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.contextStrict<RsPath>()
        val structLiteral = path?.contextStrict<RsStructLiteral>()
        if (structLiteral != null) {
            if (structLiteral.path != path) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val targetMod = getWritablePathMod(path) ?: return null
            val name = path.referenceName ?: return null
            val place = PsiInsertionPlace.forItemInModBefore(targetMod, structLiteral) ?: return null

            text = RsBundle.message("intention.name.create.struct", name)
            return Context(name, structLiteral, targetMod, place)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newStruct = buildStruct(project, ctx) ?: return
        val inserted = ctx.place.insert(newStruct)

        val types = ctx.structLiteral.structLiteralBody.structLiteralFieldList.mapNotNull { it.expr?.type }
        RsImportHelper.importTypeReferencesFromTys(inserted, types)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val fields = inserted.blockFields?.namedFieldDeclList.orEmpty().map { it.createSmartPointer() }
        if (editor.canRunTemplateFor(inserted) && fields.isNotEmpty()) {
            val unknownTypes = inserted.descendantsOfType<RsInferType>()
            val tpl = editor.newTemplateBuilder(inserted)

            // Replace unknown types
            unknownTypes.forEach {
                tpl.replaceElement(it)
            }

            // Replace field names
            for (field in fields) {
                val element = field.element?.identifier ?: continue
                val variable = tpl.introduceVariable(element)
                val fieldLiteralIdentifier = ctx.structLiteral.structLiteralBody.structLiteralFieldList.find {
                    it.identifier?.text == element.text
                }?.identifier ?: continue
                variable.replaceElementWithVariable(fieldLiteralIdentifier)
            }
            tpl.runInline()
        } else {
            inserted.navigate(true)
        }
    }

    private fun buildStruct(project: Project, ctx: Context): RsStructItem? {
        val factory = RsPsiFactory(project)
        val visibility = getVisibility(ctx.targetMod, ctx.structLiteral.containingMod)
        val fields = generateFields(ctx.structLiteral, visibility)
        return factory.tryCreateStruct("${visibility}struct ${ctx.name}$fields")
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    companion object {
        fun generateFields(structLiteral: RsStructLiteral, visibility: String): String {
            val fieldList = structLiteral.structLiteralBody.structLiteralFieldList
            val fieldsJoined = fieldList.joinToString(separator = ",\n") {
                val name = it.referenceName
                val expr = it.expr
                val type = when {
                    expr != null -> expr.type
                    else -> it.resolveToBinding()?.type ?: TyUnknown
                }

                "$visibility$name: ${type.renderInsertionSafe(includeLifetimeArguments = true)}"
            }
            return " {\n$fieldsJoined\n}"
        }
    }
}
