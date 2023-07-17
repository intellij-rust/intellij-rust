/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.tools.cargo
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*

class AddTokioMainFix(function: RsFunction) : RsQuickFixBase<RsFunction>(function) {
    private val hasTokio = function.findDependencyCrateRoot("tokio") != null
    override fun getFamilyName() = RsBundle.message("intention.name.add.tokio.main")
    override fun getText(): String {
        return if (hasTokio) {
            RsBundle.message("intention.name.add.tokio.main")
        } else {
            RsBundle.message("intention.name.install.tokio.and.add.main")
        }

    }
    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        if (!element.isAsync) {
            val anchor = element.unsafe ?: element.externAbi ?: element.fn
            element.addBefore(RsPsiFactory(project).createAsyncKeyword(), anchor)
        }
        val anchor = element.outerAttrList.firstOrNull() ?: element.firstKeyword
        element.addOuterAttribute(Attribute("tokio::main"), anchor)

        if (!element.isIntentionPreviewElement && !hasTokio) {
            installTokio(project)
        }
    }
    private fun installTokio(project: Project) {
        val cargo = project.toolchain?.cargo() ?: return
        object : Task.Backgroundable(project, RsBundle.message("progress.title.adding.dependency", "tokio")) {
            override fun shouldStartInBackground(): Boolean = true
            override fun run(indicator: ProgressIndicator) {
                cargo.addDependency(project, "tokio", listOf("full"))
            }
            override fun onSuccess() {
                project.cargoProjects.refreshAllProjects()
            }
        }.queue()
    }
}
