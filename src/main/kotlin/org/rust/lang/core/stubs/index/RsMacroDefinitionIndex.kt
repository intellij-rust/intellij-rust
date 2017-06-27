/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.stubs.RsFileStub

class RsMacroDefinitionIndex : StringStubIndexExtension<RsMacroDefinition>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsMacroDefinition> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsMacroDefinition> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustMacroDefinitionIndex")
    }
}
