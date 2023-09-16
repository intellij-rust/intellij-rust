/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.addMissingFieldsToStructLiteral
import org.rust.lang.core.psi.RsPsiFactory

class AddStructFieldsLiteralRecursiveIntention : AddStructFieldsLiteralIntention() {

    override fun getText() = RsBundle.message("intention.name.recursively.replace.with.actual.fields")

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val structLiteral = ctx.structLiteral
        removeDotsAndBaseStruct(structLiteral)
        addMissingFieldsToStructLiteral(RsPsiFactory(project), editor, structLiteral, true)
    }
}
