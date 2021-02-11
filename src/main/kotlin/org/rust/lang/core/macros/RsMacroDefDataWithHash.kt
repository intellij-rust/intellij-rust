/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.bodyHash
import org.rust.stdext.HashCode

class RsMacroDefDataWithHash(val data: RsMacroDefData, val bodyHash: HashCode?) {
    constructor(def: RsMacro) : this(RsMacroDefData(def), def.bodyHash)
}
