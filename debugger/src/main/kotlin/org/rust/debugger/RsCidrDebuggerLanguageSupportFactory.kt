package org.rust.debugger

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.OCDebuggerLanguageSupportFactory
import com.jetbrains.cidr.execution.debugger.OCDebuggerTypesHelper
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.ResolveEngine
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

class RsCidrDebuggerLanguageSupportFactory : OCDebuggerLanguageSupportFactory() {
    override fun createTypesHelper(process: CidrDebugProcess?): CidrDebuggerTypesHelper = object : OCDebuggerTypesHelper(process) {
        override fun resolveToDeclaration(position: XSourcePosition, `var`: LLValue): PsiElement? {
            if (PsiManager.getInstance(process!!.project).findFile(position.file) !is RsFile) {
                return super.resolveToDeclaration(position, `var`)
            }

            val context = CidrDebuggerTypesHelper.getDefaultContextElement(position, process.project)
            return resolveToDeclaration(context, `var`.name)
        }
    }
}


private fun resolveToDeclaration(ctx: PsiElement?, name: String): PsiElement? {
    val composite = ctx?.parentOfType<RsCompositeElement>() ?: return null
    val path = RustPath.Named(RustPathSegment(name, emptyList()), emptyList())
    return ResolveEngine.resolve(path, composite, Namespace.Values).firstOrNull()
}
