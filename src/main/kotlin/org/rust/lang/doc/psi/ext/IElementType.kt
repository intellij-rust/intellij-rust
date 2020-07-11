/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi.ext

import com.intellij.psi.tree.IElementType
import org.rust.lang.doc.psi.RsDocElementTypes

val IElementType.isDocCommentLeafToken: Boolean
    get() = this == RsDocElementTypes.DOC_GAP || this == RsDocElementTypes.DOC_DATA
