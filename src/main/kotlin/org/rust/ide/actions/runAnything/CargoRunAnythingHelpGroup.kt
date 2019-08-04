/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup

// BACKCOMPAT: 2019.1
@Suppress("DEPRECATION")
class CargoRunAnythingHelpGroup: RunAnythingHelpGroup<RunAnythingProvider<*>>() {
    override fun getProviders(): Collection<RunAnythingProvider<*>> =
        RunAnythingProvider.EP_NAME.extensions.filterIsInstance<CargoRunAnythingProvider>()

    override fun getTitle(): String = "Cargo"
}
