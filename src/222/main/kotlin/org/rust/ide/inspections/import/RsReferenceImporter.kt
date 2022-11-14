/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsFile

class RsReferenceImporter : ReferenceImporter {
    override fun autoImportReferenceAtCursor(editor: Editor, file: PsiFile): Boolean = false

    override fun isAddUnambiguousImportsOnTheFlyEnabled(file: PsiFile): Boolean =
        file is RsFile && RsCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly
}
