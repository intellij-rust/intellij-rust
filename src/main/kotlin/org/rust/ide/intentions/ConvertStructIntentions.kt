/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.RsBundle
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.convertStruct.RsConvertToNamedFieldsAction
import org.rust.ide.refactoring.convertStruct.RsConvertToTupleAction

class ConvertToStructIntention : RsRefactoringAdaptorIntention() {
    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsConvertToNamedFieldsAction()

    override fun getText() = RsBundle.message("intention.name.convert.to.struct")
    override fun getFamilyName() = text

}

class ConvertToTupleIntention : RsRefactoringAdaptorIntention() {
    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsConvertToTupleAction()

    override fun getText() = RsBundle.message("intention.name.convert.to.tuple")
    override fun getFamilyName() = text
}
