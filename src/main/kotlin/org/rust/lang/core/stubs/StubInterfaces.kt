/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

interface RsNamedStub {
    val name: String?
}

interface RsVisibilityStub {
    val isPublic: Boolean
}
