package org.rust.debugger.lang

import com.intellij.psi.PsiElement
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import com.jetbrains.cidr.execution.debugger.evaluation.CidrMemberValue
import org.rust.lang.core.psi.RsCodeFragmentFactory
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

class RsDebuggerTypesHelper(process: CidrDebugProcess) : CidrDebuggerTypesHelper(process) {
    override fun computeSourcePosition(value: CidrMemberValue): XSourcePosition? = null

    override fun isImplicitContextVariable(position: XSourcePosition, `var`: LLValue): Boolean? = false

    override fun resolveProperty(value: CidrMemberValue, dynamicTypeName: String?): XSourcePosition? = null

    override fun resolveToDeclaration(position: XSourcePosition, `var`: LLValue): PsiElement? {
        if (!isRust(position)) return delegate?.resolveToDeclaration(position, `var`)

        val context = getContextElement(position)
        return resolveToDeclaration(context, `var`.name)
    }

    private val delegate: CidrDebuggerTypesHelper? =
        RsDebuggerLanguageSupportFactory.DELEGATE?.createTypesHelper(process)

    private fun isRust(position: XSourcePosition): Boolean =
        getContextElement(position).containingFile is RsFile
}

private fun resolveToDeclaration(ctx: PsiElement?, name: String): PsiElement? {
    val composite = ctx?.getNextNonCommentSibling()?.parentOfType<RsCompositeElement>(strict = false)
        ?: return null
    val expr = RsCodeFragmentFactory(composite.project).createLocalVariable(name, composite)
        ?: return null

    return expr.path.reference.resolve()
}
