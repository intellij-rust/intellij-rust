/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplateMultiple
import org.rust.openapiext.createSmartPointer

class DestructureIntention : RsElementBaseIntentionAction<DestructureIntention.Context>() {
    override fun getText(): String = "Use destructuring declaration"
    override fun getFamilyName(): String = text

    sealed class Context(val patIdent: RsPatIdent) {
        class Struct(patIdent: RsPatIdent, val struct: RsStructItem, val usages: Map<String, List<PsiReference>>) :
            Context(patIdent)

        class Tuple(patIdent: RsPatIdent, val tuple: TyTuple, val usages: Map<String, List<PsiReference>>) :
            Context(patIdent)
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val patIdent = element.ancestorStrict<RsPatIdent>() ?: return null

        return when (val ty = patIdent.patBinding.type) {
            is TyAdt -> {
                val refs = ReferencesSearch
                    .search(patIdent.patBinding)
                    .asSequence()
                    .toList()
                val usages = refs
                    .mapNotNull {
                        it.element.ancestorStrict<RsDotExpr>()?.fieldLookup
                    }
                    .groupBy({ it.identifier!!.text },
                        { it.reference })
                val struct = ty.item as? RsStructItem ?: return null
                val mod = element.contextStrict<RsMod>() ?: return null
                if (!struct.canBeInstantiatedIn(mod)) return null
                Context.Struct(patIdent, struct, usages)
            }
            is TyTuple -> {
                val usages = ReferencesSearch
                    .search(element)
                    .filter {
                        it.element.childrenOfType<RsFieldLookup>().isNotEmpty()
                    }
                    .groupBy {
                        it.element.childrenOfType<RsFieldLookup>().first().integerLiteral!!.text
                    }
                Context.Tuple(patIdent, ty, usages)
            }
            else -> null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        when (ctx) {
            is Context.Struct -> handleStruct(project, editor, ctx.patIdent, ctx.usages, ctx.struct)
            is Context.Tuple -> handleTuple(project, editor, ctx.patIdent, ctx.usages, ctx.tuple.types.size)
        }
    }

    private fun handleStruct(project: Project,
                             editor: Editor,
                             patIdent: RsPatIdent,
                             usages: Map<String, List<PsiReference>>,
                             struct: RsStructItem) {
        val factory = RsPsiFactory(project)
        val patStruct = if (struct.isTupleStruct) {
            factory.createPatTupleStruct(struct)
        } else {
            factory.createPatStruct(struct)
        }
        val newStruct = patIdent.replace(patStruct)
        if (struct.namedFields.isEmpty()) return
        when (newStruct) {
            is RsPatTupleStruct -> {
                replaceFieldPatsWithPlaceholders(editor, newStruct, newStruct
                    .childrenOfType<RsPat>()
                    .map {
                        val sp = it.createSmartPointer()
                        val refs = usages[it.text] ?: return
                        Pair(sp, refs)
                    })
            }
            is RsPatStruct -> {
                usages.forEach { (field, fieldUsages) ->
                    fieldUsages.forEach {
                        val fieldLookup = it.element as? RsFieldLookup ?: return@forEach
                        val dotExpr = fieldLookup.parentDotExpr
                        val pathExpr = RsPsiFactory(project).tryCreatePathExpr(field) ?: return
                        dotExpr.replace(pathExpr)
                    }
                }
            }
        }
    }

    private fun handleTuple(project: Project,
                            editor: Editor,
                            patIdent: RsPatIdent,
                            usages: Map<String, List<PsiReference>>,
                            fieldNum: Int) {
        val patTuple = RsPsiFactory(project).createPatTuple(fieldNum)
        val newPatTuple = patIdent.replace(patTuple) as? RsPatTup ?: return
        replaceFieldPatsWithPlaceholders(editor, newPatTuple, newPatTuple
            .childrenOfType<RsPat>()
            .map {
                val sp = it.createSmartPointer()
                val refs = usages[it.text] ?: return
                Pair(sp, refs)
            })
    }

    companion object {
        /**
         * Creates a replacement boxes for the pat (struct / tuple) fields.
         * Then shows the live template and initiates editing process.
         */
        private fun replaceFieldPatsWithPlaceholders(editor: Editor,
                                                     newPat: RsPat,
                                                     refs: List<Pair<SmartPsiElementPointer<RsPat>, List<PsiReference>>>) {
            editor.buildAndRunTemplateMultiple(newPat, refs)
        }
    }
}
