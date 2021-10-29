/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.addFmtStringArgument

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

// BACKCOMPAT: 2021.2. Merge with `RsAddFmtStringArgumentEditorTextFieldBase`
class RsAddFmtStringArgumentEditorTextField(
    project: Project,
    document: Document
) : RsAddFmtStringArgumentEditorTextFieldBase(project, document) {
    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
        setUpEditorComponent(editor)
    }
}
