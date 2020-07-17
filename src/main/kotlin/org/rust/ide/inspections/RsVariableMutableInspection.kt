/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.RemoveMutFix
import org.rust.ide.inspections.usageAnalysis.RsUsageAnalysisInspection
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.psi.ext.topLevelPattern
import org.rust.lang.core.types.analysis.DeclarationKind
import org.rust.lang.core.types.analysis.ProblematicDeclaration
import org.rust.lang.core.types.unusedMutability

class RsVariableMutableInspection : RsUsageAnalysisInspection() {
    override fun getLint(element: PsiElement): RsLint? = RsLint.UnusedMutability

    override fun getProblematicDeclarations(function: RsFunction): List<ProblematicDeclaration> =
        function.unusedMutability?.mutableDeclarations ?: emptyList()

    override fun getErrorMessage(binding: RsPatBinding, name: String, kind: DeclarationKind): String {
        val isSimplePat = binding.topLevelPattern is RsPatIdent
        return if (isSimplePat) {
            when (kind) {
                DeclarationKind.Parameter -> "Parameter `$name` does not need to be mutable"
                DeclarationKind.Variable -> "Variable `$name` does not need to be mutable"
            }
        } else {
            "Binding `$name` does not need to be mutable"
        }
    }

    override fun getFixes(
        binding: RsPatBinding,
        name: String,
        kind: DeclarationKind
    ): List<LocalQuickFix> = listOf(RemoveMutFix())

    override fun getElementToHighlight(binding: RsPatBinding): PsiElement? = binding.bindingMode?.mut
}
