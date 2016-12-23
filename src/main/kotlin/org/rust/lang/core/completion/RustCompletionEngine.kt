package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny

object RustCompletionEngine {
    fun completePath(ref: RustPathElement, namespace: Namespace?): Array<out LookupElement> {
        val path = ref.asRustPath ?: return emptyArray()

        return if (path.segments.isNotEmpty()) {
            val qual = path.dropLastSegment()
            RustResolveEngine.resolve(qual, ref, Namespace.Types).firstOrNull().completionsFromResolveScope()
        } else {
            RustResolveEngine.enumerateScopesFor(ref)
                .flatMap { RustResolveEngine.declarations(it, pivot = ref) }
                .filterByNamespace(namespace)
                .completionsFromScopeEntries()
        }
    }

    fun completeUseGlob(glob: RustUseGlobElement): Array<out LookupElement> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()

    fun completeFieldName(field: RustStructExprFieldElement): Array<out LookupElement> =
        field.parentOfType<RustStructExprElement>()
            ?.fields.orEmpty()
            .completionsFromNamedElements()

    fun completeFieldOrMethod(field: RustFieldExprElement): Array<out LookupElement> {
        val dispatchType = field.expr.resolvedType.stripAllRefsIfAny()

        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fields: List<RustNamedElement> = (dispatchType as? RustStructType)?.item?.namedFields.orEmpty()

        val methods = RustImplIndex.findNonStaticMethodsFor(dispatchType, field.project)

        return (fields + methods.toList()).completionsFromNamedElements()
    }
}

private fun RustCompositeElement?.completionsFromResolveScope(): Array<LookupElement> =
    if (this is RustResolveScope)
        RustResolveEngine.declarations(this, searchFor = SearchFor.PRIVATE).completionsFromScopeEntries()
    else
        emptyArray()

private fun Sequence<ScopeEntry>.completionsFromScopeEntries(): Array<LookupElement> =
    mapNotNull {
        it.element?.createLookupElement(it.name)
    }.toList().toTypedArray()

private fun Collection<RustNamedElement>.completionsFromNamedElements(): Array<LookupElement> =
    mapNotNull {
        val name = it.name ?: return@mapNotNull null
        it.createLookupElement(name)
    }.toTypedArray()

fun RustCompositeElement.createLookupElement(scopeName: String): LookupElement {
    val base = LookupElementBuilder.create(this, scopeName)
        .withIcon(getIcon(0))

    return when (this) {
        is RustStaticItemElement -> base.withTypeText(type?.text)
        is RustFieldDeclElement -> base.withTypeText(type?.text)

        is RustFnElement -> base
            .withTypeText(retType?.type?.text ?: "()")
            .withTailText(parameters?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .withInsertHandler handler@ { context: InsertionContext, lookupElement: LookupElement ->
                if (context.isInUseBlock()) return@handler
                val argsCount = parameters?.parameterList?.size ?: 0
                context.document.insertString(context.selectionEndOffset, "()")
                EditorModificationUtil.moveCaretRelatively(context.editor, if (argsCount > 0) 1 else 2)
            }

        is RustStructItemElement -> base
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null -> tupleFields!!.text
                else -> ""
            })

        is RustEnumVariantElement -> base
            .withTypeText(parentOfType<RustEnumItemElement>()?.name ?: "")
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null ->
                    tupleFields!!.tupleFieldDeclList.map { it.type.text }.joinToString(prefix = "(", postfix = ")")
                else -> ""
            })
            .withInsertHandler handler@ { context, lookupElement ->
                if (context.isInUseBlock()) return@handler
                if (tupleFields != null) {
                    context.document.insertString(context.selectionEndOffset, "()")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 1)
                } else if (blockFields != null) {
                    context.document.insertString(context.selectionEndOffset, " {}")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 2)
                }
            }

        else -> base
    }
}

private fun RustPath.dropLastSegment(): RustPath {
    check(segments.isNotEmpty())
    val segments = segments.subList(0, segments.size - 1)
    return when (this) {
        is RustPath.CrateRelative -> RustPath.CrateRelative(segments)
        is RustPath.ModRelative -> RustPath.ModRelative(level, segments)
        is RustPath.Named -> RustPath.Named(head, segments)
    }
}

private fun InsertionContext.isInUseBlock(): Boolean {
    val element = file.findElementAt(startOffset - 1)!!
    return (element.parentOfType<RustUseGlobListElement>() != null)
            || (element.parentOfType<RustUseItemElement>() != null)
}
