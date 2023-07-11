/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.intentions.visibility.ChangeVisibilityIntention
import org.rust.lang.core.psi.ext.RsVisibilityOwner

class MakePublicFix private constructor(
    element: RsVisibilityOwner,
    elementName: String?,
    private val withinOneCrate: Boolean
) : RsQuickFixBase<RsVisibilityOwner>(element) {
    @IntentionName
    private val _text = RsBundle.message("intention.name.make.public", elementName?:"")
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.make.public")
    override fun getText(): String = _text

    override fun invoke(project: Project, editor: Editor?, element: RsVisibilityOwner) {
        ChangeVisibilityIntention.makePublic(element, withinOneCrate)
    }

    companion object {
        fun createIfCompatible(
            visible: RsVisibilityOwner,
            elementName: String?,
            crateRestricted: Boolean
        ): MakePublicFix? {
            if (!ChangeVisibilityIntention.isValidVisibilityOwner(visible)) return null
            return MakePublicFix(visible, elementName, crateRestricted)
        }
    }
}
