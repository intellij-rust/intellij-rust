/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.ide.impl.TrustChangeNotifier
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.model.cargoProjects

// BACKCOMPAT: 2021.3. Replace with implementation of `com.intellij.ide.impl.TrustStateListener`
@Suppress("UnstableApiUsage")
class RsTrustChangeNotifier : TrustChangeNotifier {
    override fun projectTrusted(project: Project) {
        // Update notification provided by `RsUntrustedNotificationProvider`
        EditorNotifications.getInstance(project).updateAllNotifications()
        // Load project model that wasn't load before because project wasn't trusted
        project.cargoProjects.refreshAllProjects()
    }
}
