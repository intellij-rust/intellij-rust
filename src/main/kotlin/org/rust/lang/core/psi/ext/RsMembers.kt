/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.StubBasedPsiElement
import org.rust.lang.core.psi.*

/** Returns all members including those produced by macros */
val RsMembers.expandedMembers: List<RsAbstractable>
    get() {
        val members = mutableListOf<RsAbstractable>()

        for (member in childrenSequence) {
            when (member) {
                is RsAbstractable -> members.add(member)
                is RsMacroCall -> member.collectAbstractableMembersRecursively(members)
            }
        }

        return members
    }

private val StubBasedPsiElement<*>.childrenSequence
    get() = (greenStub?.childrenStubs?.asSequence()?.map { it.psi } ?: generateSequence(firstChild) { it.nextSibling })
        .filterIsInstance<RsElement>()

private fun RsMacroCall.collectAbstractableMembersRecursively(members: MutableList<RsAbstractable>) {
    processExpansionRecursively {
        if (it is RsAbstractable) {
            members.add(it)
        }

        false
    }
}

val List<RsAbstractable>.functions: List<RsFunction>
    get() = filterIsInstance<RsFunction>()
val List<RsAbstractable>.constants: List<RsConstant>
    get() = filterIsInstance<RsConstant>()
val List<RsAbstractable>.types: List<RsTypeAlias>
    get() = filterIsInstance<RsTypeAlias>()

val List<RsAbstractable>.functionsAndConstants: List<RsAbstractable>
    get() = filter { it is RsFunction || it is RsConstant }
