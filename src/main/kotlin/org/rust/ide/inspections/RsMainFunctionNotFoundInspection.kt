/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.CompilerFeature.Companion.START
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.processExpandedItemsExceptImplsAndUses
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsMainFunctionNotFoundInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file is RsFile) {
                    if (file.childOfType<PsiErrorElement>() != null) return

                    val crate = file.crate.asNotFake ?: return
                    if (!crate.kind.canHaveMainFunction) return
                    if (!file.isCrateRoot) return

                    if (file.queryAttributes.hasAttribute("no_main")) return
                    if (START.availability(file) == FeatureAvailability.AVAILABLE) return
                    val hasMainFunction = file.processExpandedItemsExceptImplsAndUses { it is RsFunction && "main" == it.name }
                    if (!hasMainFunction) {
                        RsDiagnostic.MainFunctionNotFound(file, crate.presentableName).addToHolder(holder)
                    }
                }
            }
        }
}
