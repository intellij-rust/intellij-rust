/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsUnaryExpr

val RsUnaryExpr.isDereference: Boolean get() = operatorType == UnaryOperator.DEREF

val RsUnaryExpr.raw: PsiElement? get() = node.findChildByType(RsElementTypes.RAW)?.psi
