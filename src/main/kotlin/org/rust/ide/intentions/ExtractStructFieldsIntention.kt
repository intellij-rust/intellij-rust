/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.RsBundle
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.extractStructFields.RsExtractStructFieldsAction

class ExtractStructFieldsIntention : RsRefactoringAdaptorIntention() {
    override fun getText(): String = RsBundle.message("action.Rust.RsExtractStructFields.intention.text")
    override fun getFamilyName(): String = text

    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsExtractStructFieldsAction()
}
