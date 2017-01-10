package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import org.rust.cargo.project.PackageOrigin
import org.rust.cargo.project.workspace.cargoProject
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.valueParameters
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.*
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny

object RustCompletionEngine {
    const val KEYWORD_PRIORITY = 10.0

    fun completePath(ref: RustPathElement, namespace: Namespace?): Array<out LookupElement> {
        val path = ref.asRustPath ?: return emptyArray()

        return if (path.segments.isNotEmpty()) {
            val qual = path.dropLastSegment()
            RustResolveEngine.resolve(qual, ref, Namespace.Types).firstOrNull()
                .completionsFromResolveScope()
        } else {
            innerDeclarations(ref)
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
        val receiverType = field.expr.resolvedType.stripAllRefsIfAny()

        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fields: List<RustNamedElement> = (receiverType as? RustStructType)?.item?.namedFields.orEmpty()

        val methods = receiverType.getNonStaticMethodsIn(field.project).toList()

        return (fields + methods).completionsFromNamedElements()
    }

    fun completeMethod(call: RustMethodCallExprElement): Array<out LookupElement> {
        val receiverType = call.expr.resolvedType.stripAllRefsIfAny()
        return receiverType.getNonStaticMethodsIn(call.project).toList().completionsFromNamedElements()
    }

    fun completeExternCrate(extCrate: RustExternCrateItemElement): Array<out LookupElement> =
        extCrate.module?.cargoProject?.packages
            ?.filter { it.origin == PackageOrigin.DEPENDENCY }
            ?.mapNotNull { it.libTarget }
            ?.map { LookupElementBuilder.create(extCrate, it.normName).withIcon(extCrate.getIcon(0)) }
            ?.toTypedArray() ?: emptyArray()
}

private fun RustCompositeElement?.completionsFromResolveScope(): Array<LookupElement> =
    if (this == null)
        emptyArray()
    else
        (outerDeclarations(this, withMethods = true) ?: emptySequence()).completionsFromScopeEntries()

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
        .withIcon(if (this is RustFile) RustIcons.MODULE else getIcon(0))

    return when (this) {
        is RustConstantElement -> base.withTypeText(type?.text)
        is RustFieldDeclElement -> base.withTypeText(type?.text)

        is RustFunctionElement -> base
            .withTypeText(retType?.type?.text ?: "()")
            .withTailText(valueParameterList?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .withInsertHandler handler@ { context: InsertionContext, lookupElement: LookupElement ->
                if (context.isInUseBlock) return@handler
                if (context.alreadyHasParens) return@handler
                context.document.insertString(context.selectionEndOffset, "()")
                EditorModificationUtil.moveCaretRelatively(context.editor, if (valueParameters.isEmpty()) 2 else 1)
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
                if (context.isInUseBlock) return@handler
                val (text, shift) = when {
                    tupleFields != null -> Pair("()", 1)
                    blockFields != null -> Pair(" {}", 2)
                    else -> return@handler
                }
                context.document.insertString(context.selectionEndOffset, text)
                EditorModificationUtil.moveCaretRelatively(context.editor, shift)
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

private val InsertionContext.isInUseBlock: Boolean
    get() = file.findElementAt(startOffset - 1)!!.parentOfType<RustUseItemElement>() != null

private val InsertionContext.alreadyHasParens: Boolean get() {
    val parent = file.findElementAt(startOffset)!!.parentOfType<RustExprElement>()
    return (parent is RustMethodCallExprElement) || parent?.parent is RustCallExprElement
}
