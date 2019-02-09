/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import com.jetbrains.cidr.execution.debugger.evaluation.CidrMemberValue
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.resolve.VALUES
import org.rust.lang.core.resolve.processNestedScopesUpwards

class RsDebuggerTypesHelper(process: CidrDebugProcess) : CidrDebuggerTypesHelper(process) {
    override fun createReferenceFromText(`var`: LLValue, context: PsiElement): PsiReference? = null

    override fun computeSourcePosition(value: CidrMemberValue): XSourcePosition? = null

    override fun isImplicitContextVariable(position: XSourcePosition, `var`: LLValue): Boolean? = false

    override fun resolveProperty(value: CidrMemberValue, dynamicTypeName: String?): XSourcePosition? = null

    override fun resolveToDeclaration(position: XSourcePosition, `var`: LLValue): PsiElement? {
        val context = getContextElement(position)
        return resolveToDeclaration(context, `var`.name)
    }
}

private fun resolveToDeclaration(ctx: PsiElement?, name: String): PsiElement? {
    val composite = ctx?.ancestorOrSelf<RsElement>() ?: return null

    var resolved: PsiElement? = null
    processNestedScopesUpwards(composite, VALUES) { entry ->
        if (entry.name == name) {
            resolved = entry.element
            true
        } else {
            false
        }
    }

    return resolved
}
