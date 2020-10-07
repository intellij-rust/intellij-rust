/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.SerializedStubTreeDataExternalizer
import com.intellij.psi.stubs.StubForwardIndexExternalizer
import org.jetbrains.annotations.NotNull

// BACKCOMPAT: 2020.2. Inline it
@Suppress("UnstableApiUsage")
fun newSerializedStubTreeDataExternalizer(
    manager: SerializationManagerEx,
    externalizer: StubForwardIndexExternalizer<*>,
): SerializedStubTreeDataExternalizer {
    return SerializedStubTreeDataExternalizer(
        manager,
        externalizer
    )
}
