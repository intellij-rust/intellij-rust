/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.types.regions.Scope

class Scopes {
    var ifThenScope: IfThenScope? = null
    val unwindDrops = DropTree()

    private val breakableScopes = mutableListOf<BreakableScope>()
    private val stack = mutableListOf<MirScope>()

    fun push(scope: MirScope) {
        stack.add(scope)
    }

    fun pop() {
        stack.removeLast()
    }

    fun topmost(): Scope {
        return stack.last().regionScope
    }

    fun last(): MirScope {
        return stack.last()
    }

    fun scopeIndex(scope: Scope): Int {
        return stack.indexOfLast { it.regionScope == scope }
    }

    fun scopes(reversed: Boolean = false): Sequence<MirScope> {
        return if (reversed) stack.asReversed().asSequence() else stack.asSequence()
    }

    fun pushBreakable(scope: BreakableScope) {
        breakableScopes.add(scope)
    }

    fun popBreakable() {
        breakableScopes.removeLast()
    }

    fun reversedBreakableScopes(): Sequence<BreakableScope> {
        return breakableScopes.asReversed().asSequence()
    }
}
