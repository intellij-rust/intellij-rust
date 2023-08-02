/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.console

import com.intellij.execution.console.ConsoleRootType
import com.intellij.ide.scratch.RootType
import org.rust.RsBundle

class RsConsoleRootType internal constructor() : ConsoleRootType("rs", RsBundle.message("rust.consoles")) {
    companion object {
        val instance: RsConsoleRootType
            get() = RootType.findByClass(RsConsoleRootType::class.java)
    }
}
