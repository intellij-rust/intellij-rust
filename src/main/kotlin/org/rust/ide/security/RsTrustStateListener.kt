/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.ide.impl.TrustStateListener
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects

@Suppress("UnstableApiUsage")
class RsTrustStateListener : TrustStateListener {

    override fun onProjectTrusted(project: Project) {
        // Since 2023.1, this callback can be called in the background thread which EDT waits for.
        // In combination with fact that we call `invokeAndWaitIfNeeded` inside `refreshAllProjects`,
        // it may lead to deadlock.
        // Let's postpone invocation of `refreshAllProjects` to next event if this code is executed in background thread
        runInEdt {
            // Load project model that wasn't load before because project wasn't trusted
            project.cargoProjects.refreshAllProjects()
        }
    }
}
