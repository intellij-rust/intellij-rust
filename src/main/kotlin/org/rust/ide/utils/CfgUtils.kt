/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.isCfgUnknownSelf
import org.rust.lang.core.psi.ext.isEnabledByCfgSelf

val PsiElement.isEnabledByCfg: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().all { it.isEnabledByCfgSelf }

val PsiElement.isCfgUnknown: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().any { it.isCfgUnknownSelf }
