/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrEvaluator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.evaluation.CidrEvaluatedValue
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsEvaluator(frame: CidrStackFrame) : CidrEvaluator(frame) {
    override fun getExpressionRangeAtOffset(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): TextRange? {
        return runReadAction {
            PsiDocumentManager.getInstance(project).getPsiFile(document)?.let { file ->
                findSuitableExpression(file, offset)?.textRange
            }
        }
    }

    private fun findSuitableExpression(file: PsiFile, offset: Int): PsiElement? {
        val pathExpr = file.findElementAt(offset)?.ancestorOrSelf<RsPathExpr>() ?: return null
        val resolved = pathExpr.path.reference?.resolve()
        return if (resolved is RsPatBinding) pathExpr else null
    }

    override fun doEvaluate(driver: DebuggerDriver, position: XSourcePosition?, expr: XExpression): CidrEvaluatedValue {
        val v = driver.evaluate(myFrame.threadId, myFrame.frameIndex, expr.expression)
        return CidrEvaluatedValue(v, myFrame.process, position, myFrame, expr.expression)
    }
}
