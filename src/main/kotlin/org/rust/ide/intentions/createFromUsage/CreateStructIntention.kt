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
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

class CreateStructIntention : RsElementBaseIntentionAction<CreateStructIntention.Context>() {
    override fun getFamilyName() = "Create struct"

    class Context(val name: String, val literalElement: RsStructLiteral, val target: RsMod)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>()
        val structLiteral = path?.parentOfType<RsStructLiteral>()
        if (structLiteral != null) {
            if (structLiteral.path != path) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val target = getTargetModForStruct(path) ?: return null
            val name = path.referenceName ?: return null

            text = "Create struct `$name`"
            return Context(name, structLiteral, target)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val struct = buildStruct(project, ctx) ?: return
        val function = ctx.literalElement.parentOfType<RsFunction>() ?: return
        val inserted = insertStruct(ctx.target, struct, function)

        val types = ctx.literalElement.structLiteralBody.structLiteralFieldList.mapNotNull { it.expr?.type }
        RsImportHelper.importTypeReferencesFromTys(inserted, types)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val fields = inserted.blockFields?.namedFieldDeclList.orEmpty().map { it.createSmartPointer() }
        if (inserted.containingFile == function.containingFile && fields.isNotEmpty()) {
            val unknownTypes = inserted.descendantsOfType<RsBaseType>()
                .filter { it.underscore != null }
                .map { it.createSmartPointer() }
            val builder = editor.newTemplateBuilder(inserted.containingFile) ?: return

            // Replace unknown types
            unknownTypes.forEach {
                builder.replaceElement(it.element ?: return@forEach)
            }

            // Replace field names
            for (field in fields) {
                val element = field.element?.identifier ?: continue
                val variable = builder.introduceVariable(element)
                val fieldLiteralIdentifier = ctx.literalElement.structLiteralBody.structLiteralFieldList.find {
                    it.identifier?.text == element.text
                }?.identifier ?: continue
                variable.replaceElementWithVariable(fieldLiteralIdentifier)
            }
            builder.runInline()
        } else {
            inserted.navigate(true)
        }
    }

    private fun buildStruct(project: Project, ctx: Context): RsStructItem? {
        val factory = RsPsiFactory(project)
        val fieldList = ctx.literalElement.structLiteralBody.structLiteralFieldList
        val visibility = getVisibility(ctx.target, ctx.literalElement.containingMod)

        val fieldsJoined = fieldList.joinToString(separator = ",\n") {
            val name = it.referenceName
            val expr = it.expr
            val type = when {
                expr != null -> expr.type
                else -> it.resolveToBinding()?.type ?: TyUnknown
            }

            "$visibility$name: ${type.renderInsertionSafe(includeLifetimeArguments = true)}"
        }
        val suffix = when {
            fieldsJoined.isEmpty() -> ";"
            else -> " {\n$fieldsJoined\n}"
        }

        return factory.tryCreateStruct("${visibility}struct ${ctx.name}$suffix")
    }
}

private fun getTargetModForStruct(path: RsPath): RsMod? = when {
    path.qualifier != null -> getWritablePathTarget(path) as? RsMod
    else -> path.containingMod
}
