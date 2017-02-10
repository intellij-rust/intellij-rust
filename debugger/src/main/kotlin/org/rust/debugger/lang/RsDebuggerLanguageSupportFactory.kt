package org.rust.debugger.lang

import com.intellij.execution.configurations.RunProfile
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupportFactory
import com.jetbrains.cidr.execution.debugger.CidrEvaluator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import org.rust.debugger.runconfig.CargoDebugConfiguration

class RsDebuggerLanguageSupportFactory : CidrDebuggerLanguageSupportFactory() {

    override fun createEditor(profile: RunProfile): XDebuggerEditorsProvider? {
        if (profile !is CargoDebugConfiguration) return null
        return RsDebuggerEditorsProvider()
    }

    override fun createEditor(breakpoint: XBreakpoint<out XBreakpointProperties<Any>>?): XDebuggerEditorsProvider? = null

    override fun createTypesHelper(process: CidrDebugProcess): CidrDebuggerTypesHelper = RsDebuggerTypesHelper(process)

    override fun createEvaluator(frame: CidrStackFrame): CidrEvaluator? = null

    companion object {
        // HACK: currently `CidrDebuggerTypesHelper` is tied to the process and not to the
        // language of the stack frame, so we must delegate to existing TypesHelpers manually
        val DELEGATE: CidrDebuggerLanguageSupportFactory? by lazy {
            CidrDebuggerLanguageSupportFactory.EP_NAME.extensions
                .asSequence()
                .filter { it !is RsDebuggerLanguageSupportFactory }
                .firstOrNull()
        }
    }
}
