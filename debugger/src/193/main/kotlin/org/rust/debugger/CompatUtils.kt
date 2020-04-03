/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import java.io.File

@Suppress("UNUSED_PARAMETER")
fun downloadDebugger(onSuccess: (File) -> Unit, onFailure: () -> Unit) {
    error("Shouldn't be called")
}
