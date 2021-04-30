/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

// BACKCOMPAT: 2020.2
@Deprecated("Use org.rust.cargo.toolchain.tools.Cargo instead")
class Cargo(toolchain: RsToolchainBase, useWrapper: Boolean = false) : org.rust.cargo.toolchain.tools.Cargo(toolchain, useWrapper)
