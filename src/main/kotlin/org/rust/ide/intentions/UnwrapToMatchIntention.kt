/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.UnwrapToMatchIntention.ReceiverType.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class UnwrapToMatchIntention: RsElementBaseIntentionAction<UnwrapToMatchIntention.Context>() {
    override fun getText() = "Replace .unwrap() with match"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val methodCall = element.ancestorOrSelf<RsMethodCall>() ?: return null
        val itemType = (methodCall.receiver.type as? TyAdt)?.item as? RsEnumItem ?: return null
        val enumType = getReceiverType(itemType) ?: return null

        if (methodCall.referenceName == "unwrap" &&
            methodCall.typeArgumentList == null &&
            methodCall.valueArgumentList.exprList.isEmpty()) {
            return Context(methodCall, enumType)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (methodCall, enumType) = ctx
        val generatedCode = buildString {
            append("match ${methodCall.receiver.text} {")
            append("${enumType.valueBranch},")
            append("${enumType.nonValueMatcher} => unimplemented!(),")
            append("}")
        }

        val matchExpression = RsPsiFactory(project).createExpression(generatedCode) as RsMatchExpr
        methodCall.parentDotExpr.replace(matchExpression)
    }

    private fun getReceiverType(item: RsEnumItem): ReceiverType? {
        val knownItems = item.knownItems
        return when(item) {
            knownItems.Option -> OPTION
            knownItems.Result -> RESULT
            else -> null
        }
    }

    data class Context (
        val methodCall: RsMethodCall,
        val receiverType: ReceiverType
    )

    enum class ReceiverType(val valueBranch: String, val nonValueMatcher: String) {
        OPTION("Some(x) => x", "None"),
        RESULT("Ok(x) => x", "Err(_)"),
    }
}
