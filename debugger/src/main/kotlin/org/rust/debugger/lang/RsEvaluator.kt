/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrEvaluator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver
import com.jetbrains.cidr.execution.debugger.evaluation.CidrEvaluatedValue
import org.rust.debugger.statistics.RsDebuggerUsageCollector
import org.rust.debugger.statistics.RsDebuggerUsageCollector.ExpressionKind
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.toPsiFile
import org.rust.stdext.RsResult
import org.rust.stdext.unwrapOrThrow
import java.util.*

class RsEvaluator(frame: CidrStackFrame) : CidrEvaluator(frame) {
    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean
    ): TextRange? = runReadAction {
        document.toPsiFile(project)?.let { file ->
            findSuitableExpression(file, offset)?.textRange
        }
    }

    private fun findSuitableExpression(file: PsiFile, offset: Int): PsiElement? {
        val pathExpr = file.findElementAt(offset)?.ancestorOrSelf<RsPathExpr>() ?: return null
        val resolved = pathExpr.path.reference?.resolve()
        return if (resolved is RsPatBinding) pathExpr else null
    }

    override fun doEvaluate(driver: DebuggerDriver, position: XSourcePosition?, expr: XExpression): CidrEvaluatedValue {
        val project = myFrame.process.project
        val context = position?.file?.toPsiFile(project)
        val result = try {
            val v = driver.evaluate(myFrame.thread, myFrame.frame, expr.expression)
            RsResult.Ok(CidrEvaluatedValue(v, myFrame.process, position, myFrame, expr.expression))
        } catch (e: Throwable) {
            RsResult.Err(e)
        }
        if (context is RsFile) {
            logEvaluatedElements(project, driver, expr, context, result.isOk)
        }
        return result.unwrapOrThrow()
    }

    private fun logEvaluatedElements(
        project: Project,
        driver: DebuggerDriver,
        expr: XExpression,
        context: RsFile,
        success: Boolean,
    ) {
        val debuggerKind = when (driver) {
            is GDBDriver -> RsDebuggerUsageCollector.DebuggerKind.GDB
            is LLDBDriver -> RsDebuggerUsageCollector.DebuggerKind.LLDB
            else -> RsDebuggerUsageCollector.DebuggerKind.Unknown
        }
        ReadAction.nonBlocking<EnumSet<ExpressionKind>> { RsDebuggerUsageCollector.collectUsedElements(expr, context) }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) {
                features -> RsDebuggerUsageCollector.logEvaluated(success, debuggerKind, features)
            }
            .submit(NonUrgentExecutor.getInstance())

    }
}
