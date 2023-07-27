/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.document
import org.rust.toml.addCargoDependency
import org.rust.toml.getPackageCargoTomlFile

class AddTokioMainFix(function: RsFunction) : RsQuickFixBase<RsFunction>(function) {
    override fun getFamilyName(): String = RsBundle.message("intention.name.add.tokio.main")
    override fun getText(): String = RsBundle.message("intention.name.add.tokio.main")

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        if (!element.isAsync) {
            val anchor = element.unsafe ?: element.externAbi ?: element.fn
            element.addBefore(RsPsiFactory(project).createAsyncKeyword(), anchor)
        }

        val anchor = element.outerAttrList.firstOrNull() ?: element.firstKeyword
        element.addOuterAttribute(Attribute("tokio::main"), anchor)

        if (!element.isIntentionPreviewElement) {
            element.containingCrate.addCargoDependency("tokio", "1.0.0", REQUIRED_TOKIO_FEATURES)
            element.containingCrate.cargoTarget?.pkg?.getPackageCargoTomlFile(project)?.document?.let {
                FileDocumentManager.getInstance().saveDocument(it)
            }

            project.cargoProjects.refreshAllProjects()
        }
    }

    companion object {
        private val REQUIRED_TOKIO_FEATURES = listOf("rt", "rt-multi-thread", "macros")
    }
}
