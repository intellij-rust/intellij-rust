package org.rust.cargo.project.workspace

import com.intellij.execution.ExecutionException
import com.intellij.util.messages.Topic
import org.rust.cargo.project.CargoProjectDescription

/**
 * Interface to subscribe for the Cargo-backed project updates. That's a rather low-level API
 */
interface CargoProjectWorkspaceListener {

    /**
     * Called every time Cargo's project gets description updated, no
     * matter whether did it actually changed from the previous update or not
     */
    fun onWorkspaceUpdateCompleted(r: UpdateResult)

    sealed class UpdateResult {
        class Ok(val projectDescription: CargoProjectDescription) : UpdateResult()
        class Err(val error: ExecutionException) : UpdateResult()
    }

    object Topics {
        val UPDATES = Topic("org.rust.CargoProjectUpdatesTopic", CargoProjectWorkspaceListener::class.java)
    }
}
