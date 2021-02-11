/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.core.psi.ext.macroBodyStubbed

class RsMacroDefData(val macroBody: Lazy<RsMacroBody?>) {
    constructor(def: RsMacro) : this(lazy(LazyThreadSafetyMode.PUBLICATION) { def.macroBodyStubbed })
}
