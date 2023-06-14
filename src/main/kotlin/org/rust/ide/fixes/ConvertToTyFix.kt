/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import org.rust.ide.presentation.render
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.ty.Ty

abstract class ConvertToTyFix(
    expr: RsExpr,
    private val tyName: String,
    private val convertSubject: String
) : RsQuickFixBase<RsExpr>(expr) {

    constructor(expr: RsExpr, ty: Ty, convertSubject: String) :
        this(expr, ty.render(), convertSubject)

    override fun getFamilyName(): String = "Convert to type"
    override fun getText(): String = "Convert to $tyName using $convertSubject"
}
