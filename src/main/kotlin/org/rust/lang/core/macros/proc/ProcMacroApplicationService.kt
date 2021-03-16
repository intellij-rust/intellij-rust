/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

@Service
class ProcMacroApplicationService : Disposable {

    private var sharedServer: ProcMacroServerPool? = null

    @Synchronized
    fun getServer(): ProcMacroServerPool? {
        if (!isEnabled()) return null

        var server = sharedServer
        if (server == null) {
            server = ProcMacroServerPool.tryCreate(this)
            sharedServer = server
        }
        return server
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): ProcMacroApplicationService = service()
        fun isEnabled(): Boolean = isFeatureEnabled(RsExperiments.PROC_MACROS)
            && isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)
    }
}
