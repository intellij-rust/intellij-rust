/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import io.github.z4kn4fein.semver.toVersionOrNull
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsElementTypes.CSELF
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*

class RsDeprecationInspection : RsLintInspection() {
    override fun getDisplayName() = "Deprecated item"

    override fun getLint(element: PsiElement): RsLint = RsLint.Deprecated

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitElement(ref: RsElement) {
            // item is non-inline module declaration or not reference element
            if (ref is RsModDeclItem || ref !is RsReferenceElement) return

            val original = ref.reference?.resolve() ?: return
            val identifier = ref.referenceNameElement ?: return

            // ignore `Self` identifier
            if (identifier.elementType == CSELF) return

            val targetElement = when (original) {
                is RsFile -> original.declaration
                is RsAbstractable -> if (original.owner.isTraitImpl) original.superItem else original
                else -> original
            } ?: return

            checkAndRegisterAsDeprecated(identifier, targetElement, holder)
        }
    }

    private fun checkAndRegisterAsDeprecated(identifier: PsiElement, original: PsiElement, holder: RsProblemsHolder) {
        if (original is RsOuterAttributeOwner) {
            val attr = original.queryAttributes.deprecatedAttribute ?: return
            val (message, highlightType) = attr.extractDeprecatedMessage(identifier.text)
            holder.registerLintProblem(identifier, message, highlightType)
        }
    }

    private fun RsMetaItem.extractDeprecatedMessage(item: String): Pair<String, RsLintHighlightingType> {
        val (note, since) = if (DEPRECATED_ATTR_NAME == name) {
            extract(NOTE_PARAM_NAME, SINCE_PARAM_NAME)
        } else {
            extract(REASON_PARAM_NAME, SINCE_PARAM_NAME)
        }

        return if (isPresentlyDeprecated(since)) {
            buildString {
                append("`$item` is deprecated")
                if (since != null) append(" since $since")
                if (note != null) append(": $note")
            } to RsLintHighlightingType.DEPRECATED
        } else {
            buildString {
                append("`$item` will be deprecated from $since")
                if (note != null) append(": $note")
            } to RsLintHighlightingType.WEAK_WARNING
        }
    }

    // Presently as in not in the future; in the current version
    private fun RsMetaItem.isPresentlyDeprecated(since: String?): Boolean {
        // In case we can't check if the `sinceVersion` is at least the `currentVersion` just assume it is
        val sinceVersion = since?.toVersionOrNull(false) ?: return true
        val currentVersion = this.containingCargoPackage?.version?.toVersionOrNull() ?: return true

        return currentVersion >= sinceVersion
    }

    private fun RsMetaItem.extract(noteParamName: String, sinceParamName: String): DeprecatedAttribute {
        val params = metaItemArgs?.metaItemList
        val note = params?.getByName(noteParamName)
        val since = params?.getByName(sinceParamName)
        return DeprecatedAttribute(note, since)
    }

    private fun List<RsMetaItem>.getByName(name: String): String? = firstOrNull { name == it.name }?.value

    private data class DeprecatedAttribute(val note: String?, val since: String?)

    companion object {
        private const val DEPRECATED_ATTR_NAME: String = "deprecated"

        private const val SINCE_PARAM_NAME: String = "since"
        private const val NOTE_PARAM_NAME: String = "note"
        private const val REASON_PARAM_NAME: String = "reason"
    }
}
