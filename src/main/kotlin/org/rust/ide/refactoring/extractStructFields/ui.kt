/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractStructFields

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.openapiext.isUnitTestMode
import javax.swing.JComponent

private var MOCK: ExtractFieldsUi? = null

fun showExtractStructFieldsDialog(project: Project): String? {
    val chooser = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockExtractFieldsUi`")
    } else {
        ExtractFieldsDialog(project)
    }
    return chooser.selectStructName(project)
}

@TestOnly
fun withMockExtractFieldsUi(mockUi: ExtractFieldsUi, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

interface ExtractFieldsUi {
    fun selectStructName(project: Project): String?
}

private class ExtractFieldsDialog(project: Project) : DialogWrapper(project, false), ExtractFieldsUi {
    private val input: JBTextField = JBTextField()

    init {
        super.init()
        title = RsBundle.message("action.Rust.RsExtractStructFields.choose.name.dialog.title")
    }

    override fun doValidate(): ValidationInfo? {
        if (!isValidRustVariableIdentifier(input.text)) {
            ValidationInfo(RsBundle.message("action.Rust.RsExtractStructFields.choose.name.dialog.invalid.name"), input)
        }
        return null
    }

    override fun createCenterPanel(): JComponent =
        panel { row { input().focused() } }

    override fun selectStructName(project: Project): String? =
        if (showAndGet()) input.text else null
}
