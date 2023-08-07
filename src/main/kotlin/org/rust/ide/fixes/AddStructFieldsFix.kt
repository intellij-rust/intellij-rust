/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.addMissingFieldsToStructLiteral
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteral

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    structBody: RsStructLiteral,
    private val recursive: Boolean = false
) : RsQuickFixBase<RsStructLiteral>(structBody) {
    override fun getText(): String {
        return if (recursive) {
            RsBundle.message("intention.name.recursively.add.missing.fields")
        } else {
            RsBundle.message("intention.name.add.missing.fields")
        }
    }

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsStructLiteral) {
        addMissingFieldsToStructLiteral(RsPsiFactory(project), editor, element, recursive)
    }
}
