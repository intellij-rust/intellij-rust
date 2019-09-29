/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.isEnabledByCfg

/**
 * Element is conditionally enabled iff it is not conditionally disabled by any of parent [RsDocAndAttributeOwner]
 */
val PsiElement.isEnabledByCfg: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().all { it.isEnabledByCfg }
