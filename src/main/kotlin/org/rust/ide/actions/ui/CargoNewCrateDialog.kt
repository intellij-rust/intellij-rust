/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.TestOnly
import org.rust.ide.newProject.RsPackageNameValidator
import org.rust.ide.ui.layout
import org.rust.openapiext.isUnitTestMode
import javax.swing.JComponent

data class CargoNewCrateSettings(val binary: Boolean, val crateName: String)

interface CargoNewCrateUI {
    fun selectCargoCrateSettings(): CargoNewCrateSettings?
}

private var MOCK: CargoNewCrateUI? = null

fun showCargoNewCrateUI(
    project: Project,
    root: VirtualFile
): CargoNewCrateUI {
    return if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockCargoNewCrateUi`")
    } else {
        CargoNewCrateDialog(project, root)
    }
}

@TestOnly
fun withMockCargoNewCrateUi(mockUi: CargoNewCrateUI, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

class CargoNewCrateDialog(project: Project, private val root: VirtualFile) : DialogWrapper(project), CargoNewCrateUI {
    private val typeCombobox = ComboBox<String>(arrayOf("Binary", "Library"))
    private val name = JBTextField(20)

    val binary get() = this.typeCombobox.selectedIndex == 0
    val crateName get() = name.text.trim()

    init {
        title = "New Cargo crate"
        init()
    }

    override fun selectCargoCrateSettings(): CargoNewCrateSettings? {
        val result = showAndGet()
        if (!result) return null
        return CargoNewCrateSettings(binary, crateName)
    }

    override fun createCenterPanel(): JComponent {

        return layout {
            row("Name", name)
            row("Type", typeCombobox)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return name
    }

    override fun doValidate(): ValidationInfo? {
        val name = crateName

        val validationError = RsPackageNameValidator.validate(name, binary)
        validationError?.let {
            return ValidationInfo(it, this.name)
        }

        if (root.findChild(name) != null) return ValidationInfo("Directory $name already exists.", this.name)
        return null
    }
}
