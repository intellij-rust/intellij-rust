/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.stubs.RsLitExprStub

val RsLitExpr.integerLiteralValue: String? get() =
    (stub as? RsLitExprStub)?.integerLiteralValue ?: integerLiteral?.text
