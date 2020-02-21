/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.notification.NotificationType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.ide.notifications.showBalloonWithoutProject
import org.rust.lang.core.completion.getElementOfType
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.isAncestorOf
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import kotlin.reflect.KProperty


fun tomlPluginIsAbiCompatible(): Boolean = computeOnce

private val computeOnce: Boolean by lazy {
    try {
        load<TomlLiteralKind>()
        load(TomlLiteral::kind)
        true
    } catch (e: LinkageError) {
        showBalloonWithoutProject(
            "Incompatible TOML plugin version, code completion for Cargo.toml is not available.",
            NotificationType.WARNING
        )
        false
    }
}

private inline fun <reified T : Any> load(): String = T::class.java.name
private fun load(p: KProperty<*>): String = p.name

val TomlKey.isDependencyKey: Boolean
    get() {
        val text = text
        return text == "dependencies" || text == "dev-dependencies" || text == "build-dependencies"
    }

val TomlKey.isFeaturesKey: Boolean
    get() {
        val text = text
        return text == "features"
    }

val TomlTableHeader.isDependencyListHeader: Boolean
    get() = names.lastOrNull()?.isDependencyKey == true

val TomlTableHeader.isFeatureListHeader: Boolean
    get() = names.lastOrNull()?.isFeaturesKey == true

/** Inserts `=` between key and value if missed and wraps inserted string with quotes if needed */
class StringValueInsertionHandler(private val keyValue: TomlKeyValue) : InsertHandler<LookupElement> {
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

fun getClosestKeyValueAncestor(position: PsiElement): TomlKeyValue? {
    val parent = position.parent ?: return null
    val keyValue = parent.ancestorOrSelf<TomlKeyValue>()
        ?: error("PsiElementPattern must not allow values outside of TomlKeyValues")
    // If a value is already present we should ensure that the value is a literal
    // and the caret is inside the value to forbid completion in cases like
    // `key = "" <caret>`
    val value = keyValue.value
    return when {
        value == null || !(value !is TomlLiteral || !value.isAncestorOf(position)) -> keyValue
        else -> null
    }
}
