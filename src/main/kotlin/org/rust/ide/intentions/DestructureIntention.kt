/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.rust.ide.presentation.render
import org.rust.ide.refactoring.findBinding
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type

class DestructureIntention : RsElementBaseIntentionAction<DestructureIntention.Context>() {
    override fun getText(): String = "Use destructuring declaration"
    override fun getFamilyName(): String = text

    sealed class Context(val patIdent: RsPatIdent) {
        class Struct(patIdent: RsPatIdent, val struct: RsStructItem, val type: TyAdt) : Context(patIdent)
        class Tuple(patIdent: RsPatIdent, val tuple: TyTuple) : Context(patIdent)
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val patIdent = element.ancestorStrict<RsPatIdent>() ?: return null
        return when (val ty = patIdent.patBinding.type) {
            is TyAdt -> {
                val struct = ty.item as? RsStructItem ?: return null
                val mod = element.contextStrict<RsMod>() ?: return null
                if (!struct.canBeInstantiatedIn(mod)) return null
                Context.Struct(patIdent, struct, ty)
            }
            is TyTuple -> Context.Tuple(patIdent, ty)
            else -> null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val patBinding = ctx.patIdent.patBinding
        val factory = RsPsiFactory(patBinding.project)
        val usages = ReferencesSearch.search(patBinding, patBinding.getSearchScope())
            .map { it.element }
            .filterIsInstance<RsPath>()

        val bindings = findBindings(usages, patBinding)

        val toRename = mutableListOf<RsPatBinding>()
        val (newPat, replaceContext) = createDestructuredPat(ctx, factory, toRename, bindings)

        if (ctx is Context.Struct) {
            importTypeReferencesFromTy(newPat, ctx.type)
        }

        when (replaceContext) {
            is ReplaceContext.Tuple -> replaceTupleUsages(replaceContext, factory, usages)
            is ReplaceContext.Struct -> replaceStructUsages(replaceContext, factory, usages)
        }

        if (toRename.isNotEmpty()) {
            renameNewBindings(newPat, editor, toRename)
        }
    }

    private fun findBindings(usages: List<RsPath>, patBinding: RsPatBinding): HashSet<String> {
        val bindings = HashSet<String>()
        usages.forEach {
            bindings.addAll(it.getAllVisibleBindings())
        }
        // The binding itself will be removed
        bindings.remove(patBinding.identifier.text)
        return bindings
    }

    private fun createDestructuredPat(
        ctx: Context,
        factory: RsPsiFactory,
        toRename: MutableList<RsPatBinding>,
        bindings: HashSet<String>
    ): Pair<RsElement, ReplaceContext> {
        return when (ctx) {
            is Context.Struct -> {
                val typeName = ctx.type.render(includeTypeArguments = false)
                val struct = ctx.struct

                if (struct.isTupleStruct) {
                    val newStruct = factory.createPatTupleStruct(struct, typeName)
                    val replaced = ctx.patIdent.replace(newStruct) as RsPatTupleStruct

                    val fieldNames = allocateTupleFieldNames(bindings, replaced, toRename, struct.positionalFields.size)
                    replaced to ReplaceContext.Tuple(struct.positionalFields.size, fieldNames, struct.name)
                } else {
                    val newStruct = factory.createPatStruct(struct, typeName)
                    val replaced = ctx.patIdent.replace(newStruct) as RsPatStruct

                    val fieldNamesList = struct.blockFields?.namedFieldDeclList?.mapNotNull {
                        it.name
                    }.orEmpty()
                    val fieldNames = allocateStructFieldNames(bindings, replaced, toRename, fieldNamesList)
                    replaced to ReplaceContext.Struct(struct, fieldNames)
                }
            }
            is Context.Tuple -> {
                val fieldCount = ctx.tuple.types.size
                val patTuple = factory.createPatTuple(fieldCount)
                val replaced = ctx.patIdent.replace(patTuple) as RsPatTup

                val fieldNames = allocateTupleFieldNames(bindings, replaced, toRename, fieldCount)
                replaced to ReplaceContext.Tuple(fieldCount, fieldNames)
            }
        }
    }

    companion object {
        /**
         * Creates replacement boxes for the pat (struct / tuple) fields.
         * Then shows a live template and initiates the editing process.
         */
        private fun renameNewBindings(context: RsElement, editor: Editor, toBeRenamed: List<RsPatBinding>) {
            val project = context.project
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

            val builder = editor.newTemplateBuilder(context.containingFile) ?: return
            for (binding in toBeRenamed) {
                val variable = builder.introduceVariable(binding)
                ReferencesSearch.search(binding, binding.getSearchScope()).forEach {
                    variable.replaceElementWithVariable(it.element)
                }
            }
            builder.runInline()
        }
    }
}

private fun PsiElement.getSearchScope(): SearchScope =
    LocalSearchScope(ancestorStrict<RsBlock>() ?: ancestorStrict<RsFunction>() ?: this)

private fun allocateStructFieldNames(
    bindings: HashSet<String>,
    destructuredElement: RsPatStruct,
    toRename: MutableList<RsPatBinding>,
    fieldNames: List<String>
): Map<String, String> {
    val factory = RsPsiFactory(destructuredElement.project)
    val allocatedNames = fieldNames.associateWith { allocateName(bindings, it) }

    destructuredElement.patFieldList.forEach {
        val binding = it.patBinding ?: return@forEach
        val name = binding.name ?: return@forEach
        val newName = allocatedNames.getOrDefault(name, name)
        if (name != newName) {
            val patField = binding.replace(factory.createPatFieldFull(name, newName)) as RsPatFieldFull
            val newBinding = patField.findBinding()
            if (newBinding != null) {
                toRename.add(newBinding)
            }
        }
    }
    return allocatedNames
}

private fun allocateTupleFieldNames(
    bindings: HashSet<String>,
    destructuredElement: RsElement,
    toRename: MutableList<RsPatBinding>,
    fieldCount: Int
): List<String> {
    val factory = RsPsiFactory(destructuredElement.project)

    val fieldNames = (0..fieldCount).map { allocateName(bindings, "_$it") }
    val fields = destructuredElement.childrenOfType<RsPatIdent>()
    fields.zip(fieldNames).forEach { (field, name) ->
        val inserted = field.patBinding.identifier.replace(factory.createIdentifier(name))
        val binding = inserted.parentOfType<RsPatBinding>()
        if (binding != null) {
            toRename.add(binding)
        }
    }
    return fieldNames
}

private fun replaceStructUsages(context: ReplaceContext.Struct, factory: RsPsiFactory, usages: List<RsPath>) {
    usages.forEach { element ->
        val dot = element.parent.parent as? RsDotExpr
        if (dot?.methodCall != null) return@forEach

        val field = dot?.fieldLookup?.identifier
        if (field != null) {
            val name = field.text
            dot.replace(factory.createExpression(context.fieldNames.getOrDefault(name, name)))
        } else if (element.parent is RsPathExpr) {
            val name = context.struct.name ?: return@forEach
            val fields = context.struct.blockFields?.namedFieldDeclList?.mapNotNull { f ->
                val fieldName = context.fieldNames.getOrDefault(f.name, f.name)
                if (fieldName == f.name) {
                    fieldName
                } else {
                    "${f.name}: $fieldName"
                }
            } ?: return@forEach
            val structLiteral = factory.createStructLiteral(
                name,
                fields.joinToString(", ", prefix = "{ ", postfix = " }")
            )
            element.parent.replace(structLiteral)
        }
    }
}

private fun replaceTupleUsages(context: ReplaceContext.Tuple, factory: RsPsiFactory, usages: List<RsPath>) {
    usages.forEach { element ->
        val dot = element.parent.parent as? RsDotExpr
        if (dot?.methodCall != null) return@forEach

        val index = dot?.fieldLookup?.integerLiteral
        val indexNumber = index?.text?.toIntOrNull()
        if (indexNumber != null) {
            dot.replace(factory.createExpression(context.fieldNames.getOrNull(indexNumber) ?: "_${index.text}"))
        } else if (element.parent is RsPathExpr) {
            val prefix = "${context.structName ?: ""}("
            val tupleExpr = factory.createExpression(List(context.fieldCount) { i ->
                context.fieldNames.getOrNull(i) ?: "_$i"
            }.joinToString(", ", prefix = prefix, postfix = ")"))
            element.parent.replace(tupleExpr)
        }
    }
}

private fun allocateName(bindings: HashSet<String>, baseName: String): String {
    var name = baseName
    var index = 0
    while (name in bindings) {
        name = "$baseName${index}"
        index += 1
    }
    bindings.add(name)
    return name
}

private sealed class ReplaceContext {
    data class Struct(val struct: RsStructItem, val fieldNames: Map<String, String>) : ReplaceContext()
    data class Tuple(
        val fieldCount: Int,
        val fieldNames: List<String>,
        val structName: String? = null
    ) : ReplaceContext()
}
