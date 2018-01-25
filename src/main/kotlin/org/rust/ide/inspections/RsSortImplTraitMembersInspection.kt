/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.intentions.SortImplTraitMembersIntention
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor

class RsSortImplTraitMembersInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitImplItem(o: RsImplItem) {
            if (SortImplTraitMembersIntention.isApplicableTo(o) == null) return
            holder.registerProblem(
                o,
                "Different impl member order from the trait",
                IntentionWrapper(SortImplTraitMembersIntention(), o.containingFile)
            )
        }
    }
}
