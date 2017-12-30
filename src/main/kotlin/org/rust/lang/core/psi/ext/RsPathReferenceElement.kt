/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.resolve.ref.RsPathReference

interface RsPathReferenceElement : RsReferenceElement {
    override fun getReference(): RsPathReference
}
