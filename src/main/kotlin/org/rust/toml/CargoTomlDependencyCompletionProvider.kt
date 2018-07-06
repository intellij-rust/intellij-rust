/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.getElementOfType
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.isAncestorOf
import org.toml.lang.psi.*

abstract class TomlKeyValueCompletionProviderBase : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val parent = parameters.position.parent ?: return
        if (parent is TomlKey) {
            val keyValue = parent.parent as? TomlKeyValue
                ?: error("PsiElementPattern must not allow keys outside of TomlKeyValues")
            completeKey(keyValue, result)
        } else {
            val keyValue = parent.ancestorOrSelf<TomlKeyValue>()
                ?: error("PsiElementPattern must not allow values outside of TomlKeyValues")
            // If a value is already present we should ensure that the value is a literal
            // and the caret is inside the value to forbid completion in cases like
            // `key = "" <caret>`
            val value = keyValue.value
            if (value != null && (value !is TomlLiteral || !value.isAncestorOf(parameters.position))) return

            completeValue(keyValue, result)
        }
    }

    protected abstract fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet)

    protected abstract fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet)
}

/** @see CargoTomlPsiPattern.inDependencyKeyValue */
class CargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val variants = searchCrate(keyValue.key).map { it.dependencyLine }
        result.addAllElements(variants.map(LookupElementBuilder::create))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val version = getCrateLastVersion(keyValue.key) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }
}

/** @see CargoTomlPsiPattern.inSpecificDependencyHeaderKey */
class CargoTomlSpecificDependencyHeaderCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val key = parameters.position.parent as? TomlKey ?: return
        val variants = searchCrate(key)

        val elements = variants.map { variant ->
            LookupElementBuilder.create(variant.name)
                .withPresentableText(variant.dependencyLine)
                .withInsertHandler { context, _ ->
                    val table = key.ancestorStrict<TomlTable>() ?: return@withInsertHandler
                    if (table.entries.isEmpty()) {
                        context.document.insertString(
                            context.selectionEndOffset + 1,
                            "\nversion = \"${variant.maxVersion}\""
                        )
                    }
                }
        }

        result.addAllElements(elements)
    }
}

/** @see CargoTomlPsiPattern.inSpecificDependencyKeyValue */
class CargoTomlSpecificDependencyVersionCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val dependencyNameKey = getDependencyKeyFromTableHeader(keyValue)

        val version = getCrateLastVersion(dependencyNameKey) ?: return
        result.addElement(LookupElementBuilder.create("version = \"$version\""))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val dependencyNameKey = getDependencyKeyFromTableHeader(keyValue)
        val version = getCrateLastVersion(dependencyNameKey) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }

    private fun getDependencyKeyFromTableHeader(keyValue: TomlKeyValue): TomlKey {
        val table = keyValue.parent as? TomlTable
            ?: error("PsiElementPattern must not allow keys outside of TomlTable")
        return table.header.names.lastOrNull()
            ?: error("PsiElementPattern must not allow KeyValues in tables without header")
    }
}

/** Inserts `=` between key and value if missed and wraps inserted string with quotes if needed */
private class StringValueInsertionHandler(val keyValue: TomlKeyValue) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        var startOffset = context.startOffset
        val value = context.getElementOfType<TomlValue>()
        val hasEq = keyValue.children.any { it.elementType == TomlElementTypes.EQ }
        val hasQuotes = value != null && (value !is TomlLiteral || value.literalType != TomlElementTypes.NUMBER)

        if (!hasEq) {
            context.document.insertString(startOffset - if (hasQuotes) 1 else 0, "= ")
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
            startOffset += 2
        }

        if (!hasQuotes) {
            context.document.insertString(startOffset, "\"")
            context.document.insertString(context.selectionEndOffset, "\"")
        }
    }
}

private val TomlLiteral.literalType: IElementType
    get() = children.first().elementType
