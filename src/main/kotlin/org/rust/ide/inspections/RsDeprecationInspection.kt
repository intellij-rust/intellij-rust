/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsDeprecationInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Deprecated item"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitElement(ref: RsElement) {
            // item is non-inline module declaration or not reference element
            if (ref is RsModDeclItem || ref !is RsWeakReferenceElement) return

            var original = ref.reference?.resolve() ?: return

            val identifier = ref.referenceNameElement ?: ref
            original = when (original) {
                is RsFile -> original.declaration
                is RsAbstractable -> original.superItem
                else -> original
            } ?: original

            if (original is RsOuterAttributeOwner &&
                checkAndRegisterAsDeprecated(identifier, original, holder)) return
        }
    }

    private fun checkAndRegisterAsDeprecated(identifier: PsiElement, original: RsOuterAttributeOwner, holder: ProblemsHolder): Boolean =
        original.outerAttrList
            .mapNotNull { DeprecatedAttribute.from(it) }
            .firstOrNull()
            ?.let { deprecatedAttr ->
                holder.registerProblem(identifier, deprecatedAttr.getMessage(identifier.text), ProblemHighlightType.LIKE_DEPRECATED)
                true
            } ?: false
}

private class DeprecatedAttribute(val note: String?, val since: String?) {

    fun getMessage(item: String): String = buildString {
        append("'$item' is marked as deprecated")
        if (since != null) append(" since version $since")
        if (note != null) append(" ($note)")
    }

    companion object {
        private const val DEPRECATED_ATTR_NAME: String = "deprecated"
        private const val RUSTC_DEPRECATED_ATTR_NAME: String = "rustc_deprecated"

        private const val SINCE_PARAM_NAME: String = "since"
        private const val NOTE_PARAM_NAME: String = "note"
        private const val REASON_PARAM_NAME: String = "reason"

        fun from(attr: RsOuterAttr): DeprecatedAttribute? {
            fun List<RsMetaItem>.getByName(name: String): String? =
                firstOrNull { name == it.identifier?.text }?.litExpr?.stringLiteralValue

            fun RsOuterAttr.extract(noteParamName: String, sinceParamName: String): DeprecatedAttribute {
                val params = metaItem.metaItemArgs?.metaItemList
                val note = params?.getByName(noteParamName)
                val since = params?.getByName(sinceParamName)
                return DeprecatedAttribute(note, since)
            }

            val identifier = attr.metaItem.identifier?.text
            return when (identifier) {
                DEPRECATED_ATTR_NAME -> attr.extract(NOTE_PARAM_NAME, SINCE_PARAM_NAME)
                RUSTC_DEPRECATED_ATTR_NAME -> attr.extract(REASON_PARAM_NAME, SINCE_PARAM_NAME)
                else -> null
            }
        }
    }
}
