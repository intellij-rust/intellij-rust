package org.rust.debugger.lang

import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import com.jetbrains.cidr.execution.debugger.evaluation.CidrMemberValue
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

class RsDebuggerTypesHelper(process: CidrDebugProcess) : CidrDebuggerTypesHelper(process) {
    private fun isApplicable(position: XSourcePosition): Boolean {
        return getContextElement(position).containingFile is RsFile
    }

    override fun computeSourcePosition(value: CidrMemberValue): XSourcePosition? = null

    override fun isImplicitContextVariable(position: XSourcePosition, `var`: LLValue): Boolean? = false

    override fun resolveProperty(value: CidrMemberValue, dynamicTypeName: String?): XSourcePosition? = null

    override fun resolveToDeclaration(position: XSourcePosition, `var`: LLValue): PsiElement? {
        if (!isApplicable(position)) return null

        val context = getContextElement(position)
        return resolveToDeclaration(context, `var`.name)
    }
}

private fun resolveToDeclaration(ctx: PsiElement?, name: String): PsiElement? {
    val composite = ctx?.parentOfType<RsCompositeElement>() ?: return null
    val path = RustPath.Named(RustPathSegment(name, emptyList()), emptyList())
    return ResolveEngine.resolve(path, composite, Namespace.Values).firstOrNull()
}
