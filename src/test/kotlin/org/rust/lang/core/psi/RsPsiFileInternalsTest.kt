/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.RsTestBase

class RsPsiFileInternalsTest: RsTestBase() {
    fun `test setStubTree`() {
        assertNotNull(RsPsiFileInternals.setStubTreeMethod)
    }
}
