/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.RemoveParameterFix
import org.rust.ide.inspections.fixes.RemoveVariableFix
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.ide.inspections.usageAnalysis.RsUsageAnalysisInspection
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.topLevelPattern
import org.rust.lang.core.types.analysis.DeclarationKind
import org.rust.lang.core.types.analysis.DeclarationKind.Parameter
import org.rust.lang.core.types.analysis.DeclarationKind.Variable
import org.rust.lang.core.types.analysis.ProblematicDeclaration
import org.rust.lang.core.types.liveness

class RsLivenessInspection : RsUsageAnalysisInspection() {
    override fun getLint(element: PsiElement): RsLint? = RsLint.UnusedVariables

    override fun getProblematicDeclarations(function: RsFunction): List<ProblematicDeclaration> =
        function.liveness?.deadDeclarations ?: emptyList()

    override fun getErrorMessage(binding: RsPatBinding, name: String, kind: DeclarationKind): String {
        val isSimplePat = binding.topLevelPattern is RsPatIdent
        return if (isSimplePat) {
            when (kind) {
                Parameter -> "Parameter `$name` is never used"
                Variable -> "Variable `$name` is never used"
            }
        } else {
            "Binding `$name` is never used"
        }
    }

    override fun getFixes(binding: RsPatBinding, name: String, kind: DeclarationKind): List<LocalQuickFix> {
        val isSimplePat = binding.topLevelPattern is RsPatIdent
        val fixes = mutableListOf<LocalQuickFix>(RenameFix(binding, "_$name"))
        if (isSimplePat) {
            when (kind) {
                Parameter -> fixes.add(RemoveParameterFix(binding, name))
                Variable -> fixes.add(RemoveVariableFix(binding, name))
            }
        }
        return fixes
    }
}
