/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsLambdaExpr

val RsLambdaExpr.isAsync: Boolean
    get() = node.findChildByType(RsElementTypes.ASYNC) != null
