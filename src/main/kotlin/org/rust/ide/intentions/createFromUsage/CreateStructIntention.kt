/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.ide.DataManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.RsMultipleVariableRenamer
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

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val fields = inserted.blockFields?.namedFieldDeclList.orEmpty()
        if (inserted.containingFile == function.containingFile && fields.isNotEmpty()) {
            editor.caretModel.moveToOffset(fields[0].textOffset)
            RsMultipleVariableRenamer(fields.map { it.createSmartPointer() })
                .doRename(fields[0], editor, DataManager.getInstance().getDataContext(editor.component))
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

    private fun insertStruct(targetModule: RsMod, struct: RsStructItem, sourceFunction: RsElement): RsStructItem {
        if (targetModule == sourceFunction.containingMod) {
            return sourceFunction.parent.addBefore(struct, sourceFunction) as RsStructItem
        }
        return addToModule(targetModule, struct)
    }
}

private fun getTargetModForStruct(path: RsPath): RsMod? = when {
    path.qualifier != null -> getWritablePathTarget(path) as? RsMod
    else -> path.containingMod
}
