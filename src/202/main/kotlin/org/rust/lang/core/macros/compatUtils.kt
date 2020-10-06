/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.SerializedStubTreeDataExternalizer
import com.intellij.psi.stubs.StubForwardIndexExternalizer

@Suppress("UnstableApiUsage")
fun newSerializedStubTreeDataExternalizer(
    manager: SerializationManagerEx,
    externalizer: StubForwardIndexExternalizer<*>,
): SerializedStubTreeDataExternalizer {
    return SerializedStubTreeDataExternalizer(
        /* includeInputs = */ true,
        manager,
        externalizer
    )
}
