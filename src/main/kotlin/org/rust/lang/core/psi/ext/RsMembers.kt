/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import org.rust.lang.core.psi.*
import org.rust.stdext.removeLast

/** Returns all members including those produced by macros */
val RsMembers.expandedMembers: List<RsAbstractable>
    get() {
        // Iterative DFS for macros
        val stack = mutableListOf<RsAttrProcMacroOwner>()
        val members = mutableListOf<RsAbstractable>()

        for (member in reversedChildrenSequence) {
            if (member is RsAttrProcMacroOwner) {
                stack += member
            }
        }

        while (stack.isNotEmpty()) {
            val memberOrMacroCall = stack.removeLast()
            val macroCall = when (val attr = memberOrMacroCall.procMacroAttribute) {
                is ProcMacroAttribute.Attr -> attr.attr
                is ProcMacroAttribute.Derive -> null
                null -> when (memberOrMacroCall) {
                    is RsMacroCall -> memberOrMacroCall
                    is RsAbstractable -> {
                        members += memberOrMacroCall
                        null
                    }
                    else -> null
                }
            }
            val expansion = macroCall?.expansion ?: continue
            for (expandedElement in expansion.elements.asReversed()) {
                if (expandedElement is RsAttrProcMacroOwner) {
                    stack += expandedElement
                }
            }
        }

        return members
    }

@Suppress("IfThenToElvis")
private val StubBasedPsiElement<*>.reversedChildrenSequence: Sequence<PsiElement>
    get() {
        val greenStub = greenStub
        return if (greenStub != null) {
            greenStub.childrenStubs.asReversed().asSequence().map { it.psi }
        } else {
            generateSequence(lastChild) { it.prevSibling }
        }
    }

val List<RsAbstractable>.functions: List<RsFunction>
    get() = filterIsInstance<RsFunction>()
val List<RsAbstractable>.constants: List<RsConstant>
    get() = filterIsInstance<RsConstant>()
val List<RsAbstractable>.types: List<RsTypeAlias>
    get() = filterIsInstance<RsTypeAlias>()
