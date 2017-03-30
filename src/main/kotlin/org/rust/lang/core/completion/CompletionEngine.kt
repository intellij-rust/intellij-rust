package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.asRustPath
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.resolve.*
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.RustTypificationEngine
import org.rust.lang.core.types.types.RustUnknownType
import org.rust.utils.sequenceOfNotNull

object CompletionEngine {
    const val KEYWORD_PRIORITY = 10.0

    fun completePath(ref: RsPath, namespace: Namespace?): Array<out LookupElement> {
        val path = ref.asRustPath ?: return emptyArray()

        return if (path.segments.isNotEmpty()) {
            val qual = path.dropLastSegment()
            ResolveEngine.resolve(qual, ref, Namespace.Types).firstOrNull()
                .completionsFromResolveScope()
        } else {
            lexicalDeclarations(ref)
                .filterByNamespace(namespace)
                .completionsFromScopeEntries()
        }
    }
}

private fun RsCompositeElement?.completionsFromResolveScope(): Array<LookupElement> =
    if (this == null)
        emptyArray()
    else
        sequenceOfNotNull(
            containingDeclarations(this),
            associatedDeclarations(this)
        ).flatten().completionsFromScopeEntries()

private fun Sequence<ScopeEntry>.completionsFromScopeEntries(): Array<LookupElement> =
    mapNotNull {
        it.element?.createLookupElement(it.name)
    }.toList().toTypedArray()

fun RsCompositeElement.createLookupElement(scopeName: String): LookupElement {
    val base = LookupElementBuilder.create(this, scopeName)
        .withIcon(if (this is RsFile) RsIcons.MODULE else getIcon(0))

    return when (this) {
        is RsConstant -> base.withTypeText(typeReference?.text)
        is RsFieldDecl -> base.withTypeText(typeReference?.text)

        is RsFunction -> base
            .withTypeText(retType?.typeReference?.text ?: "()")
            .withTailText(valueParameterList?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .withInsertHandler handler@ { context: InsertionContext, _: LookupElement ->
                if (context.isInUseBlock) return@handler
                if (context.alreadyHasParens) return@handler
                context.document.insertString(context.selectionEndOffset, "()")
                EditorModificationUtil.moveCaretRelatively(context.editor, if (valueParameters.isEmpty()) 2 else 1)
            }

        is RsStructItem -> base
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null -> tupleFields!!.text
                else -> ""
            })

        is RsEnumVariant -> base
            .withTypeText(parentOfType<RsEnumItem>()?.name ?: "")
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null ->
                    tupleFields!!.tupleFieldDeclList
                        .map { it.typeReference.text }
                        .joinToString(prefix = "(", postfix = ")")
                else -> ""
            })
            .withInsertHandler handler@ { context, _ ->
                if (context.isInUseBlock) return@handler
                val (text, shift) = when {
                    tupleFields != null -> Pair("()", 1)
                    blockFields != null -> Pair(" {}", 2)
                    else -> return@handler
                }
                context.document.insertString(context.selectionEndOffset, text)
                EditorModificationUtil.moveCaretRelatively(context.editor, shift)
            }

        is RsPatBinding -> base
            .withTypeText(RustTypificationEngine.typify(this).let {
                when (it) {
                    is RustUnknownType -> ""
                    else -> it.toString()
                }
            })

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
    get() = file.findElementAt(startOffset - 1)!!.parentOfType<RsUseItem>() != null

private val InsertionContext.alreadyHasParens: Boolean get() {
    val parent = file.findElementAt(startOffset)!!.parentOfType<RsExpr>()
    return (parent is RsMethodCallExpr) || parent?.parent is RsCallExpr
}
