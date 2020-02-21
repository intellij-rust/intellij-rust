/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParamsGroup
import com.intellij.remote.RemoteSdkException
import com.intellij.remote.RemoteSdkPropertiesPaths
import org.rust.ide.sdk.remote.RsRemotePathMapper
import org.rust.ide.sdk.remote.RsRemoteSocketToLocalHostProvider

@Throws(RemoteSdkException::class)
fun GeneralCommandLine.patchRemoteCommandLineIfNeeded(
    sdkData: RemoteSdkPropertiesPaths,
    socketProvider: RsRemoteSocketToLocalHostProvider,
    pathMapper: RsRemotePathMapper
) {
    val helpersPath = sdkData.helpersPath
    val interpreterPath = sdkData.interpreterPath
    val patchers = linkedMapOf<String, (ParamsGroup) -> Unit>( // Order is important
        "Debugger" to { params ->
            patchDebugParams(helpersPath, socketProvider, params)
        },
        "Profiler" to { params ->
            patchProfileParams(interpreterPath, socketProvider, params, workDirectory, pathMapper)
        },
        "Coverage" to { params ->
            patchCoverageParams(interpreterPath, params, workDirectory, pathMapper)
        }
    )

    for ((group, function) in patchers) {
        val params = parametersList.getParamsGroup(group) ?: continue
        if (params.parametersList.list.isNotEmpty()) {
            function(params)
        }
    }
}
