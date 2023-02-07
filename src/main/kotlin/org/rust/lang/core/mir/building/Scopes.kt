/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

class Scopes {
    var ifThenScope: IfThenScope? = null
    val unwindDrops = DropTree()

    private val stack = mutableListOf<Scope>()

    fun push(scope: Scope) {
        stack.add(scope)
    }

    fun pop() {
        stack.removeLast()
    }

    fun last(): Scope {
        return stack.last()
    }

    fun scopeIndex(scope: Scope): Int {
        return stack.indexOf(scope)
    }

    fun scopes(): Sequence<Scope> {
        return stack.asSequence()
    }
}
