/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsVisibility
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.types.Substitution

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

    protected abstract fun generateAccessors(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    ): List<RsFunction>?

    override fun performRefactoring(
        struct: RsStructItem,
        implBlock: RsImplItem?,
        chosenFields: List<StructMember>,
        substitution: Substitution,
        editor: Editor
    ) {
        val methods = generateAccessors(struct, implBlock, chosenFields, substitution, editor)
        methods?.firstOrNull()?.navigate(true)
    }

    override fun isFieldValid(member: StructMember, impl: RsImplItem?): Boolean {
        if (member.field.visibility == RsVisibility.Public) return false
        return impl?.expandedMembers?.all { it.name != methodName(member) } ?: true
    }
}
