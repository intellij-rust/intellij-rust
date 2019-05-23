/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.jetbrains.cidr.execution.debugger.CidrDebuggerEditorsExtensionBase
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsExpressionCodeFragment
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsDebuggerEditorsExtension : CidrDebuggerEditorsExtensionBase() {
    override fun getContext(project: Project, sourcePosition: XSourcePosition): PsiElement? =
        super.getContext(project, sourcePosition)?.ancestorOrSelf<RsElement>()

    override fun createExpressionCodeFragment(project: Project, text: String, context: PsiElement, mode: EvaluationMode): PsiFile =
        if (context is RsElement) {
            RsExpressionCodeFragment(project, text, context)
        } else {
            super.createExpressionCodeFragment(project, text, context, mode)
        }

    override fun getSupportedLanguage() = RsLanguage
}
