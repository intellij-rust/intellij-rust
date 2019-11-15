/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import org.rust.FileTreeBuilder
import org.rust.fileTree
import org.rust.lang.core.resolve.RsResolveTestBase
import org.toml.lang.psi.TomlElement

abstract class CargoTomlResolveTestBase : RsResolveTestBase() {

    protected inline fun <reified T : TomlElement> doResolveTest(noinline builder: FileTreeBuilder.() -> Unit) {
        val fileTree = fileTree(builder)
        stubOnlyResolve<T>(fileTree, resolveFileProducer = this::getActualResolveFile)
    }
}
