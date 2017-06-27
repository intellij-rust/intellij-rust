/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: Map<String, String> = emptyMap(),
    val nocapture: Boolean = true
)
