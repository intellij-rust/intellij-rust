/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class DestructureIntention : RsElementBaseIntentionAction<DestructureIntention.Context>() {
    override fun getText(): String = "Use destructuring declaration"
    override fun getFamilyName(): String = text

    sealed class Context(val patIdent: RsPatIdent) {
        class Struct(patIdent: RsPatIdent, val struct: RsStructItem) : Context(patIdent)
        class Tuple(patIdent: RsPatIdent, val tuple: TyTuple) : Context(patIdent)
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val patIdent = element.ancestorStrict<RsPatIdent>() ?: return null
        return when (val ty = patIdent.patBinding.type) {
            is TyAdt -> {
                val struct = ty.item as? RsStructItem ?: return null
                val mod = element.contextStrict<RsMod>() ?: return null
                if (!struct.canBeInstantiatedIn(mod)) return null
                Context.Struct(patIdent, struct)
            }
            is TyTuple -> Context.Tuple(patIdent, ty)
            else -> null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        when (ctx) {
            is Context.Struct -> handleStruct(project, editor, ctx.patIdent, ctx.struct)
            is Context.Tuple -> handleTuple(project, editor, ctx.patIdent, ctx.tuple.types.size)
        }
    }

    private fun handleStruct(project: Project, editor: Editor, patIdent: RsPatIdent, struct: RsStructItem) {
        val factory = RsPsiFactory(project)
        val patStruct = if (struct.isTupleStruct) {
            factory.createPatTupleStruct(struct)
        } else {
            factory.createPatStruct(struct)
        }
        val newPatStruct = patIdent.replace(patStruct) as? RsPat ?: return

        importTypeReferencesFromTy(newPatStruct, struct.declaredType)

        if (newPatStruct !is RsPatTupleStruct) return
        if (struct.positionalFields.isNotEmpty()) {
            replaceFieldPatsWithPlaceholders(editor, newPatStruct)
        }
    }

    private fun handleTuple(project: Project, editor: Editor, patIdent: RsPatIdent, fieldNum: Int) {
        val patTuple = RsPsiFactory(project).createPatTuple(fieldNum)
        val newPatTuple = patIdent.replace(patTuple) as? RsPatTup ?: return
        replaceFieldPatsWithPlaceholders(editor, newPatTuple)
    }

    companion object {
        /**
         * Creates a replacement boxes for the pat (struct / tuple) fields.
         * Then shows the live template and initiates editing process.
         */
        private fun replaceFieldPatsWithPlaceholders(editor: Editor, newPat: RsPat) {
            editor.buildAndRunTemplate(newPat, newPat.childrenOfType<RsPat>().map { it.createSmartPointer() })
        }
    }
}
