/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.util.text.StringUtil
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.refactoring.ui.MemberSelectionPanelBase
import com.intellij.ui.RowIcon
import org.apache.commons.lang.StringEscapeUtils
import org.rust.ide.docs.signature
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.RsItemElement
import javax.swing.Icon

// currently supports only move refactoring requirements
class RsMemberSelectionPanel(
    title: String,
    memberInfo: List<RsMemberInfo>
) : MemberSelectionPanelBase<
    RsItemElement,
    RsMemberInfo,
    AbstractMemberSelectionTable<RsItemElement, RsMemberInfo>
    >(title, RsMemberSelectionTable(memberInfo))

class RsMemberSelectionTable(memberInfo: List<RsMemberInfo>)
    : AbstractMemberSelectionTable<RsItemElement, RsMemberInfo>(memberInfo, null, null) {

    init {
        setTableHeader(null)
    }

    override fun getAbstractColumnValue(memberInfo: RsMemberInfo?): Any? = null

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean = false

    override fun setVisibilityIcon(memberInfo: RsMemberInfo, icon: RowIcon) {
        // we don't set visibility icon, because
        // 1) visibility is already included in item text
        // 2) some items doesn't need visibility icon (e.g. `RsImplItem`)
        //    and they will be misaligned with those items which has visibility icon
    }

    override fun getOverrideIcon(memberInfo: RsMemberInfo): Icon? = null
}

class RsMemberInfo(member: RsItemElement, isChecked: Boolean) : MemberInfoBase<RsItemElement>(member) {
    init {
        this.isChecked = isChecked
        displayName = if (member is RsModItem) {
            "mod ${member.modName}"
        } else {
            val description = buildString { member.signature(this) }
            StringEscapeUtils.unescapeHtml(StringUtil.removeHtmlTags(description))
        }
    }
}
