/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.name

class RemoveAttrFix(attr: RsAttr) : RemoveElementFix(attr, "attribute" + (attr.metaItem.name?.let { " `$it`" } ?: ""))
