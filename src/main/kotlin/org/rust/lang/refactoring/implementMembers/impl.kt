/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.TraitImplementationInfo
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.openapiext.checkWriteAccessAllowed

fun findMembersToImplement(impl: RsImplItem)
    : TraitImplementationInfo? {
    val trait = impl.traitRef?.resolveToTrait ?: return null
    val implInfo = TraitImplementationInfo.create(trait, impl) ?: return null
    if (implInfo.declared.isEmpty()) return null
    return implInfo
}

fun insertNewTraitMembers(selected: Collection<RsNamedElement>, members: RsMembers) {
    checkWriteAccessAllowed()
    if (selected.isEmpty()) return

    val templateImpl = RsPsiFactory(members.project).createMembers(
        selected.filterIsInstance<RsFunction>(),
        selected.filterIsInstance<RsTypeAlias>(),
        selected.filterIsInstance<RsConstant>()
    )
    val lastMethodOrBrace = members.functionList.lastOrNull() ?: members.lbrace ?: return
    members.addRangeAfter(
        templateImpl.lbrace.nextSibling,
        templateImpl.rbrace?.prevSibling,
        lastMethodOrBrace
    )
}

fun generateTraitMembers(impl: RsImplItem, editor: Editor?) {
    check(!ApplicationManager.getApplication().isWriteAccessAllowed)
    val implInfo = findMembersToImplement(impl) ?: run {
        if (editor != null) {
            HintManager.getInstance().showErrorHint(editor, "No members to implement have been found")
        }
        return
    }
    val chooserSelected = showTraitMemberChooser(implInfo, impl.project)
    runWriteAction {
        insertNewTraitMembers(chooserSelected, impl.members!!)
    }
}
