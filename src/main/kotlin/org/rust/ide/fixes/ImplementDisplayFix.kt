/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.refactoring.implementMembers.generateMissingTraitMembers
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.findInScope
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.resolve.knownItems

class ImplementDisplayFix(adt: RsStructOrEnumItemElement) : RsQuickFixBase<RsStructOrEnumItemElement>(adt) {
    @Nls
    private val _text = RsBundle.message("intention.name.implement.display.trait.for", requireNotNull(adt.name))

    override fun getFamilyName() =_text
    override fun getText() = _text

    override fun invoke(project: Project, editor: Editor?, element: RsStructOrEnumItemElement) {
        val knownItems = element.knownItems

        val display = knownItems.Display ?: return
        val displayName = when (element.findInScope("Display", TYPES)) {
            display -> "Display"
            null -> {
                RsImportHelper.importElement(element, display)
                "Display"
            }
            else -> "std::fmt::Display"
        }

        val placeForImpl = PsiInsertionPlace.forItemInTheScopeOf(element) ?: return
        val psiFactory = RsPsiFactory(project)
        val createdImpl = psiFactory.createTraitImplItem(
            type = element.name ?: return,
            trait = displayName,
            typeParameterList = element.typeParameterList,
            whereClause = element.whereClause,
        )
        val insertedImpl = placeForImpl.insert(createdImpl)

        generateMissingTraitMembers(insertedImpl, insertedImpl.traitRef ?: return, editor)
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        return null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }
}
