/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsPolybound
import org.rust.lang.core.psi.ext.deleteWithSurroundingPlus

class RemovePolyBoundFix(
    bound: RsPolybound,
    private val boundName: String = "`${bound.text}`"
) : RsQuickFixBase<RsPolybound>(bound) {
    override fun getText() = "Remove $boundName bound"
    override fun getFamilyName() = "Remove bound"

    override fun invoke(project: Project, editor: Editor?, bound: RsPolybound) {
        bound.deleteWithSurroundingPlus()
    }
}
