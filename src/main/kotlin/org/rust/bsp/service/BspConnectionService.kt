/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import com.intellij.openapi.components.Service
import org.rust.bsp.BspClient

@Service
interface BspConnectionService {
    fun getBspServer(): BspServer

    fun getBspClient(): BspClient

    fun doStaff()
}
