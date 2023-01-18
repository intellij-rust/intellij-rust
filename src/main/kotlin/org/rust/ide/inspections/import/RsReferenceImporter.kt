/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.ide.inspections.shouldIgnoreUnresolvedReference
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsElement
import org.rust.openapiext.runUndoTransparentWriteCommandAction
import java.util.function.BooleanSupplier

class RsReferenceImporter : ReferenceImporter {
    override fun autoImportReferenceAtCursor(editor: Editor, file: PsiFile): Boolean = false

    @Suppress("UnstableApiUsage")
    override fun computeAutoImportAtOffset(editor: Editor, file: PsiFile, offset: Int, allowCaretNearReference: Boolean): BooleanSupplier? {
        val reference = file.findReferenceAt(offset) ?: return null
        val element = reference.element as? RsElement ?: return null

        val context = when (element) {
            is RsPath -> RsUnresolvedReferenceInspection.processPath(element)?.context
            is RsMethodCall -> AutoImportFix.findApplicableContext(element)
            else -> return null
        }
        val candidate = context?.candidates?.singleOrNull() ?: return null

        if (element.shouldIgnoreUnresolvedReference()) return null

        return BooleanSupplier {
            file.project.runUndoTransparentWriteCommandAction {
                candidate.import(element)
            }
            true
        }
    }

    override fun isAddUnambiguousImportsOnTheFlyEnabled(file: PsiFile): Boolean =
        file is RsFile && RsCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly
}
