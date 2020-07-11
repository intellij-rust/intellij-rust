/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.doc.psi.RsDocComment

val PsiElement.containingDoc: RsDocComment?
    get() = ancestorOrSelf<RsDocComment>()

val PsiElement.isInDocComment: Boolean
    get() {
        val doc = containingDoc ?: return false
        return doc.elementType in RS_DOC_COMMENTS
    }
