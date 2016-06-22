package org.rust.cargo.project.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

abstract class CargoProjectWorkspaceTransientListener : CargoProjectWorkspaceListener {

    companion object {

        /**
         * Creates transient (i.e. one-time, self-disposing) listener
         */
        fun create(callback: (CargoProjectWorkspaceListener.UpdateResult) -> Unit) =
            object: CargoProjectWorkspaceTransientListener() {
                override fun onWorkspaceUpdateCompleted(r: CargoProjectWorkspaceListener.UpdateResult) {
                    callback(r)
                    Disposer.dispose(connectionDisposer)
                }
            }
    }

    /**
     * Connection disposer
     */
    val connectionDisposer: Disposable = Disposer.newDisposable()
}
