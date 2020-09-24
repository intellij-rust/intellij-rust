/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind

import com.jetbrains.cidr.cpp.valgrind.ValgrindHandler
import com.jetbrains.cidr.cpp.valgrind.ValgrindOutputConsumer

fun createValgrindConsumer(valgrindHandler: ValgrindHandler): ValgrindOutputConsumer {
    return ValgrindOutputConsumer(valgrindHandler, null)
}
