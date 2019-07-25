/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

data class CargoBuildResult(
    val succeeded: Boolean,
    val canceled: Boolean,
    val started: Long,
    val duration: Long = 0,
    val errors: Int = 0,
    val warnings: Int = 0,
    val message: String = ""
)
