/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.ide.impl.TrustStateListener
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects

@Suppress("UnstableApiUsage")
class RsTrustStateListener : TrustStateListener {

    override fun onProjectTrusted(project: Project) {
        // Load project model that wasn't load before because project wasn't trusted
        project.cargoProjects.refreshAllProjects()
    }
}
