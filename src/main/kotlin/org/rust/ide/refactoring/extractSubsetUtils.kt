/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.ext.RsOuterAttributeOwner
import org.rust.lang.core.psi.ext.name

fun findTransitiveAttributes(enum: RsOuterAttributeOwner, supportedAttributes: Set<String>): List<RsOuterAttr> =
    enum.outerAttrList.filter { it.metaItem.name in supportedAttributes }
