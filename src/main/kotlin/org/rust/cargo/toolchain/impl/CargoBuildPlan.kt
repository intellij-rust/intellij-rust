/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

data class CargoBuildPlan(
    val invocations: List<CargoBuildInvocation>
)

data class CargoBuildInvocation(
    val package_name: String,
    val package_version: String,
    val compile_mode: String,
    val env: Map<String, String>
)
