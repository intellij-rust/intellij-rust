/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVisRestriction

class FixVisRestriction(visRestriction: RsVisRestriction) : RsQuickFixBase<RsVisRestriction>(visRestriction) {

    override fun getText(): String = RsBundle.message("intention.name.fix.visibility.restriction")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsVisRestriction) {
        element.addBefore(RsPsiFactory(project).createIn(), element.path)
    }
}
