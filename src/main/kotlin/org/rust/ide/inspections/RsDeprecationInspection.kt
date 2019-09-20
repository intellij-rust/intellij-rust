/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.LIKE_DEPRECATED
import com.intellij.codeInspection.ProblemHighlightType.WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.toml.lang.CrateVersion

class RsDeprecationInspection : RsLintInspection() {
    override fun getDisplayName() = "Deprecated item"

    override val lint: RsLint = RsLint.Deprecated

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitElement(ref: RsElement) {
            // item is non-inline module declaration or not reference element
            if (ref is RsModDeclItem || ref !is RsWeakReferenceElement) return

            val original = ref.reference?.resolve() ?: return
            val identifier = ref.referenceNameElement ?: return

            val targetElement = when (original) {
                is RsFile -> original.declaration
                is RsAbstractable -> if (original.owner.isTraitImpl) original.superItem else original
                else -> original
            } ?: return

            checkAndRegisterAsDeprecated(identifier, targetElement, holder)
        }
    }

    private fun checkAndRegisterAsDeprecated(identifier: PsiElement, original: PsiElement, holder: ProblemsHolder) {
        if (original is RsOuterAttributeOwner) {
            val attr = original.queryAttributes.deprecatedAttribute ?: return
            val (message, highlightType) = attr.extractDeprecatedMessage(identifier.text)
            holder.registerProblem(identifier, message, highlightType)
        }
    }

    private fun RsMetaItem.extractDeprecatedMessage(item: String): Pair<String, ProblemHighlightType> {
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
            } to LIKE_DEPRECATED
        } else {
            buildString {
                append("`$item` will be deprecated from $since")
                if (note != null) append(": $note")
            } to WARNING
        }
    }

    // Presently as in not in the future; in the current version
    private fun RsMetaItem.isPresentlyDeprecated(since: String?): Boolean {
        // In case we can't check if the since version is at least the current version just assume it is
        val sinceVersion = CrateVersion.tryParse(since ?: return true) ?: return true
        val currentVersion = CrateVersion.tryParse(this.containingCargoPackage?.version ?: return true) ?: return true

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
