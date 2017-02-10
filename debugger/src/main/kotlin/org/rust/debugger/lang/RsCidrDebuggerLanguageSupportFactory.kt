package org.rust.debugger.lang

import com.intellij.execution.configurations.RunProfile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupportFactory
import com.jetbrains.cidr.execution.debugger.CidrEvaluator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

class RsCidrDebuggerLanguageSupportFactory : CidrDebuggerLanguageSupportFactory() {
    override fun createTypesHelper(process: CidrDebugProcess): CidrDebuggerTypesHelper = RsDebuggerTypesHelper(process)

    override fun createEvaluator(frame: CidrStackFrame): CidrEvaluator? = null

    override fun createEditor(profile: RunProfile?): XDebuggerEditorsProvider? = null

    override fun createEditor(breakpoint: XBreakpoint<out XBreakpointProperties<Any>>?): XDebuggerEditorsProvider? = null
}
