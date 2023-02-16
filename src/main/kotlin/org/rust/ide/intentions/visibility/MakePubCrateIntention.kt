/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.visibility

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.ext.RsVisibility
import org.rust.lang.core.psi.ext.RsVisibilityOwner

class MakePubCrateIntention : ChangeVisibilityIntention() {
    override val visibility: String get() = "pub(crate)"

    override fun isApplicable(element: RsVisibilityOwner): Boolean {
        val visibility = element.visibility
        return !(visibility is RsVisibility.Restricted && visibility.inMod == element.crateRoot)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        makePublic(ctx.element, crateRestricted = true)
    }
}
