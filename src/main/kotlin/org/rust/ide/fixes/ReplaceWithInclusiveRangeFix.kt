/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPatRange
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.end
import org.rust.lang.core.psi.ext.start

class ReplaceWithInclusiveRangeFix(range: RsPatRange): RsQuickFixBase<RsPatRange>(range) {
    override fun getFamilyName(): String {
        return RsBundle.message("intention.family.name.replace.with.inclusive.range")
    }

    override fun getText(): String {
        val element = myStartElement.element as? RsPatRange ?: return familyName
        val start = element.start?: return familyName
        val end = element.end ?: return familyName
        return RsBundle.message("intention.name.replace.with2", start.text + "..=" + end.text)
    }

    override fun invoke(project: Project, editor: Editor?, element: RsPatRange) {
        element.dotdot?.replace(RsPsiFactory(project).createDotDotEq())
    }
}
