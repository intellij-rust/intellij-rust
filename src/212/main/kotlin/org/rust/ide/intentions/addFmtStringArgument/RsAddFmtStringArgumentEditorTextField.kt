/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.addFmtStringArgument

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

class RsAddFmtStringArgumentEditorTextField(
    project: Project,
    document: Document
) : RsAddFmtStringArgumentEditorTextFieldBase(project, document) {

    override fun addNotify() {
        super.addNotify()
        val editor = editor ?: return
        setUpEditorComponent(editor)
    }
}
