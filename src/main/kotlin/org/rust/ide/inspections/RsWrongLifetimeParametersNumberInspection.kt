/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.util.descendants
import org.rust.ide.inspections.lints.hasMissingLifetimes
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.lifetimeArguments
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.types.lifetimeElidable
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsWrongLifetimeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitPathType(type: RsPathType) {
                val path = type.path

                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (path.valueParameterList != null) return
                if (path.cself != null) return

                val paramsDecl = path.reference?.resolve() as? RsGenericDeclaration ?: return
                val expectedLifetimes = paramsDecl.lifetimeParameters.size
                val actualLifetimes = path.lifetimeArguments.size
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

            override fun visitFunction(fn: RsFunction) {
                // https://doc.rust-lang.org/book/ch10-03-lifetime-syntax.html#lifetime-elision
                if (!fn.hasMissingLifetimes()) return

                val retType = fn.retType ?: return

                // Skipping `Fn(...) -> ...` and `fn(...) -> ...`
                val descendants = retType.descendants {
                    when (it) {
                        is RsFnPointerType -> false
                        is RsPath -> it.valueParameterList == null
                        else -> true
                    }
                }
                for (type in descendants) {
                    if (type !is RsRefLikeType) continue
                    val and = type.and ?: continue
                    if (type.lifetime != null) continue
                    RsDiagnostic.MissingLifetimeSpecifier(and).addToHolder(holder)
                }
            }
        }
}
