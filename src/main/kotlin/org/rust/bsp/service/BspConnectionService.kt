/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import com.intellij.openapi.components.Service
import org.rust.bsp.BspClient
import org.rust.cargo.toolchain.impl.CargoMetadata

@Service
interface BspConnectionService {
    fun getBspServer(): BspServer

    fun getBspClient(): BspClient

    fun connect()

    fun disconnect()

    fun doStuff()

    fun getProjectData(): CargoMetadata.Project
}
