/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.type

class GenerateAccessorIntention : RsElementBaseIntentionAction<GenerateAccessorIntention.Context>() {
    data class Context(val structItem: RsStructItem, val implItem: RsImplItem)

    override fun getText() = "Generate accessor for fields struct"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // Must be in members of an `impl` block
        if (element.parent !is RsMembers) return null
        val implItem = element.ancestorStrict<RsImplItem>() ?: return null

        // Must be implementing a struct.
        val implType = (implItem.typeReference?.type) as? TyAdt ?: return null
        val structItem = implType.item as? RsStructItem ?: return null

        // Must not be implementing a trait.
        return if (implItem.traitRef == null) Context(structItem, implItem) else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val functionNames = ctx.implItem.members?.functionList
            ?.map { it.identifier.text }
            ?: listOf()

        // Get fields in struct.
        val fields = ctx.structItem.blockFields?.fieldDeclList ?: return

        // Generate accessors for each field if there is no method name conflict.
        fields.filter { it.identifier.text !in functionNames }.forEach { field ->
            // Skip public fields since those can be accessed directly.
            if (field.isPublic) return@forEach

            val fieldName = field.identifier.text
            val fieldType = field.typeReference?.type ?: return@forEach

            val borrow = if (fieldType is TyPrimitive) "" else "&"
            val fnSignature = "fn $fieldName(&self) -> $borrow$fieldType"
            val fnBody = "${borrow}self.$fieldName"

            val accessor = RsPsiFactory(project).createTraitMethodMember(
                "$fnSignature {\n$fnBody\n}")

            ctx.implItem.members?.addBefore(accessor, ctx.implItem.members?.rbrace)
        }
    }

}
