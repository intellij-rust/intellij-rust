/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import org.rust.lang.RsLanguage

class RsDebuggerEditorsExtension : RsDebuggerEditorsExtensionBase() {
    override fun getSupportedLanguage() = RsLanguage
}
