/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.cargo

import org.rust.cargo.util.CargoCommands
import org.rust.ide.actions.runAnything.RsRunAnythingItem
import javax.swing.Icon

class RunAnythingCargoItem(command: String, icon: Icon) : RsRunAnythingItem(command, icon) {
    override val helpCommand: String = "cargo"

    override val commandDescriptions: Map<String, String> =
        CargoCommands.values().associate { it.presentableName to it.description }

    override fun getOptionsDescriptionsForCommand(commandName: String): Map<String, String>? {
        val command = CargoCommands.values().find { it.presentableName == commandName } ?: return null
        return command.options.associate { it.longName to it.description }
    }
}
