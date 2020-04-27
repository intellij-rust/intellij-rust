/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.extractEnumVariant.RsExtractEnumVariantAction

class ExtractEnumVariantIntention : RsRefactoringAdaptorIntention() {
    override fun getText(): String = "Extract enum variant"
    override fun getFamilyName(): String = text

    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsExtractEnumVariantAction()
}
