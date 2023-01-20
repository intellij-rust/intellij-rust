/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker

// BACKCOMPAT: 2022.3. Inline it
@Suppress("UnstableApiUsage", "UNUSED_PARAMETER")
fun enableAutoImportInTests(tracker: AutoImportProjectTracker, disposable: Disposable) {
    AutoImportProjectTracker.enableAutoReloadInTests(disposable)
}
