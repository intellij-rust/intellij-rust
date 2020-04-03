/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import java.io.File

// BACKCOMPAT: 2019.3. inline
fun downloadDebugger(onSuccess: (File) -> Unit, onFailure: () -> Unit) {
    RsDebuggerToolchainService.getInstance().downloadDebugger(onSuccess, onFailure)
}
