/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.util.registry.Registry

val isNewGdbSetupEnabled: Boolean get() = Registry.`is`("org.rust.debugger.gdb.setup.v2", false)
