/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import javax.swing.Icon

abstract class CargoProjectActionBase : DumbAwareAction {
    constructor(): super()
    constructor(
        text: @NlsActions.ActionText String,
        description: @NlsActions.ActionDescription String,
        icon: Icon?
    ) : super(text, description, icon)
}
