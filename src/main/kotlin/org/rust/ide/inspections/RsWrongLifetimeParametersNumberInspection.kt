/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.types.lifetimeElidable
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsWrongLifetimeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return

                val paramsDecl = type.path?.reference?.resolve() as? RsGenericDeclaration ?: return
                val expectedLifetimes = paramsDecl.typeParameterList?.lifetimeParameterList?.size ?: 0
                val actualLifetimes = type.path?.typeArgumentList?.lifetimeList?.size ?: 0
                if (expectedLifetimes == actualLifetimes) return
                if (actualLifetimes == 0 && !type.lifetimeElidable) {
                    RsDiagnostic.MissingLifetimeSpecifier(type).addToHolder(holder)
                } else if (actualLifetimes > 0) {
                    RsDiagnostic.WrongNumberOfLifetimeArguments(type, expectedLifetimes, actualLifetimes)
                        .addToHolder(holder)
                }
            }

            override fun visitRefLikeType(type: RsRefLikeType) {
                if (type.mul == null && !type.lifetimeElidable && type.lifetime == null) {
                    RsDiagnostic.MissingLifetimeSpecifier(type.and ?: type).addToHolder(holder)
                }
            }
        }

}
