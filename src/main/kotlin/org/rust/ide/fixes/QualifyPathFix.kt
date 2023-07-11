/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.import.ImportInfo
import org.rust.ide.utils.import.insertExternCrateIfNeeded
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.isIntentionPreviewElement

/**
 * Fix that qualifies a path.
 */
class QualifyPathFix(
    path: RsPath,
    @SafeFieldForPreview
    private val importInfo: ImportInfo
) : RsQuickFixBase<RsPath>(path) {
    override fun getText(): String = RsBundle.message("intention.name.qualify.path.to", importInfo.usePath)
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.qualify.path")

    override fun invoke(project: Project, editor: Editor?, element: RsPath) {
        qualify(element, importInfo)
    }

    companion object {
        fun qualify(path: RsPath, importInfo: ImportInfo) {
            val qualifiedPath = importInfo.usePath
            val fullPath = "$qualifiedPath${path.typeArgumentList?.text.orEmpty()}"
            val newPath = RsPsiFactory(path.project).tryCreatePath(fullPath) ?: return

            if (!path.isIntentionPreviewElement) {
                importInfo.insertExternCrateIfNeeded(path)
            }
            path.replace(newPath)
        }
    }
}
