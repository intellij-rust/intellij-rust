/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsPsiFactory

class AddAttrParenthesesFix(element: RsMetaItem, private val attrName: String) : RsQuickFixBase<RsMetaItem>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.add.parentheses")
    override fun getText(): String = RsBundle.message("intention.name.add.parentheses.to", attrName)

    override fun invoke(project: Project, editor: Editor?, element: RsMetaItem) {
        val newItem = RsPsiFactory(project).createOuterAttr("$attrName()").metaItem
        val replaced = element.replace(newItem) as? RsMetaItem ?: return

        // Place caret between parentheses, so the user can immediately start typing
        val offset = replaced.metaItemArgs?.lparen?.textOffset ?: return
        editor?.caretModel?.moveToOffset(offset + 1)
    }
}
