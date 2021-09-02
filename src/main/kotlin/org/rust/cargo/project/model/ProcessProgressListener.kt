/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.execution.process.ProcessListener

interface ProcessProgressListener : ProcessListener {
    fun error(title: String, message: String)
    fun warning(title: String, message: String)
}
