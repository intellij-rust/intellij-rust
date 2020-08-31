/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsVisibility
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.psi.ext.isTupleStruct

abstract class GenerateAccessorHandler : BaseGenerateHandler() {
    override fun isStructValid(struct: RsStructItem): Boolean {
        if (struct.isTupleStruct) return false
        if (struct.blockFields?.namedFieldDeclList?.isEmpty() != false) return false
        return true
    }

    /**
     * Return the method name of an accessor for the given field.
     */
    abstract fun methodName(member: StructMember): String

    override fun isFieldValid(member: StructMember, impl: RsImplItem?): Boolean {
        if (member.field.visibility == RsVisibility.Public) return false
        return impl?.expandedMembers?.all { it.name != methodName(member) } ?: true
    }
}
