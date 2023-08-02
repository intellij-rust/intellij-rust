/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMetaItemArgs
import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace
import org.rust.lang.core.psi.ext.name

class RemoveMetaItemFix(metaItem: RsMetaItem) : RemoveElementFix(metaItem, "feature" + (metaItem.name?.let { " `$it`" } ?: "")) {
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (element !is RsMetaItem) return
        val arguments = element.parent as? RsMetaItemArgs ?: return
        when (arguments.metaItemList.size) {
            0 -> return
            1 -> (arguments.parent?.parent as? RsAttr)?.delete()
            else -> element.deleteWithSurroundingCommaAndWhitespace()
        }
    }
}
