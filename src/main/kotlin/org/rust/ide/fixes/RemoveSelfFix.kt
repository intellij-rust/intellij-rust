/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.selfParameter

class RemoveSelfFix(function: RsFunction) : RsQuickFixBase<RsFunction>(function) {
    private val elementName: String = if (function.owner is RsAbstractableOwner.Impl) {
        "function"
    } else {
        "trait"
    }

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.remove.self.from", elementName)

    override fun getText(): String = RsBundle.message("intention.family.name.remove.self.from", elementName)

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        element.selfParameter?.delete()
    }
}
