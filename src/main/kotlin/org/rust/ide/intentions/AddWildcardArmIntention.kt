/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.inspections.fixes.AddRemainingArmsFix
import org.rust.ide.inspections.fixes.AddWildcardArmFix
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.lang.core.psi.RsMatchExpr

class AddWildcardArmIntention : AddRemainingArmsIntention() {

    override fun getText(): String = AddWildcardArmFix.NAME

    override fun createQuickFix(matchExpr: RsMatchExpr, patterns: List<Pattern>): AddRemainingArmsFix {
        return AddWildcardArmFix(matchExpr)
    }
}
