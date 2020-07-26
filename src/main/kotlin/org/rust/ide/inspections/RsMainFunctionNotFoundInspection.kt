/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsMainFunctionNotFoundInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        return object : RsVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file is RsFile) {
                    if (file.childOfType<PsiErrorElement>() != null) return

                    val crate = file.crate ?: return
                    if (!(crate.kind.isBin || crate.kind.isExampleBin || crate.kind.isCustomBuild)) return
                    if (!file.isCrateRoot) return

                    if (file.queryAttributes.hasAttribute("no_main")) return
                    if (file.childrenOfType<RsFunction>().lastOrNull { fn -> "main" == fn.name } != null) return

                    RsDiagnostic.MainFunctionNotFound(file, crate.presentableName).addToHolder(holder)
                }
            }
        }
    }
}
