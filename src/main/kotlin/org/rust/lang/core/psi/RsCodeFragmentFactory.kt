/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.ext.CARGO_WORKSPACE
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.openapiext.toPsiFile


class RsCodeFragmentFactory(val project: Project) {
    private val psiFactory = RsPsiFactory(project)

    fun createCrateRelativePath(pathText: String, target: CargoWorkspace.Target): RsPath? {
        check(pathText.startsWith("::"))
        val vFile = target.crateRoot ?: return null
        val crateRoot = vFile.toPsiFile(project) as? RsFile ?: return null
        return psiFactory.tryCreatePath(pathText)
            ?.apply { setContext(crateRoot) }
    }

    fun createPath(path: String, context: RsElement): RsPath? =
        psiFactory.tryCreatePath(path)?.apply {
            setContext(context)
            containingFile?.putUserData(CARGO_WORKSPACE, context.cargoWorkspace)
        }
}
