/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.components.CheckBox
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.TestOnly
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.ide.utils.import.createVirtualImportContext
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemsOwner
import org.rust.lang.core.psi.ext.RsMod
import org.rust.openapiext.document
import org.rust.openapiext.isUnitTestMode
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

typealias ChangeFunctionSignatureMock = (config: RsChangeFunctionSignatureConfig) -> Unit

private var MOCK: ChangeFunctionSignatureMock? = null

fun showChangeFunctionSignatureDialog(
    project: Project,
    config: RsChangeFunctionSignatureConfig
) {
    if (isUnitTestMode) {
        val mock = MOCK ?: error("You should set mock UI via `withMockChangeFunctionSignature`")
        mock(config)
        runChangeSignatureRefactoring(config)
    } else {
        ChangeSignatureDialog(project, SignatureDescriptor(config)).show()
    }
}

@TestOnly
fun withMockChangeFunctionSignature(mock: ChangeFunctionSignatureMock, action: () -> Unit) {
    MOCK = mock
    try {
        action()
    } finally {
        MOCK = null
    }
}

private class SignatureParameter(val factory: RsPsiFactory, val parameter: Parameter) : ParameterInfo {
    override fun getName(): String = parameter.patText
    override fun getOldIndex(): Int = parameter.index
    override fun getDefaultValue(): String = parameter.defaultValue.text
    override fun setName(name: String?) {
        if (name != null) {
            parameter.patText = name
        }
    }

    override fun getTypeText(): String = parameter.type.text

    override fun isUseAnySingleVariable(): Boolean = false
    override fun setUseAnySingleVariable(b: Boolean) {}
}

private class SignatureDescriptor(
    val config: RsChangeFunctionSignatureConfig
) : MethodDescriptor<SignatureParameter, String> {
    val function: RsFunction = config.function

    override fun getName(): String = config.name

    override fun getParameters(): List<SignatureParameter> {
        val factory = RsPsiFactory(config.function.project)
        return config.parameters.map { SignatureParameter(factory, it) }
    }

    override fun getParametersCount(): Int = config.parameters.size
    override fun getMethod(): PsiElement = config.function

    override fun getVisibility(): String = ""

    /**
     * This needs to be false, because the default dialog only offers combo boxes for visibility, but we need
     * arbitrary strings.
     */
    override fun canChangeVisibility(): Boolean = false

    override fun canChangeParameters(): Boolean = true
    override fun canChangeName(): Boolean = true
    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption = MethodDescriptor.ReadWriteOption.ReadWrite
}

private class ModelItem(
    importContext: RsMod,
    parameter: SignatureParameter
) : ParameterTableModelItemBase<SignatureParameter>(
    parameter,
    createTypeCodeFragment(importContext, parameter.parameter.parseTypeReference()),
    createExprCodeFragment(importContext),
) {
    override fun isEllipsisType(): Boolean = false
}

private class TableModel(
    val descriptor: SignatureDescriptor,
    val onUpdate: () -> Unit
) : ParameterTableModelBase<SignatureParameter, ModelItem>(
    descriptor.function,
    descriptor.function,
    NameColumn<SignatureParameter, ModelItem>(descriptor.function.project, "Pattern:"),
    SignatureTypeColumn(descriptor),
    SignatureDefaultValueColumn(descriptor)
) {
    private val factory: RsPsiFactory = RsPsiFactory(descriptor.function.project)
    private val importContext: RsMod = descriptor.function.createVirtualImportContext()

    init {
        addTableModelListener {
            onUpdate()
        }
    }

    override fun createRowItem(parameterInfo: SignatureParameter?): ModelItem {
        val parameter = if (parameterInfo == null) {
            val newParameter = createNewParameter(descriptor)
            descriptor.config.parameters.add(newParameter)
            SignatureParameter(factory, newParameter)
        } else parameterInfo

        return ModelItem(importContext, parameter)
    }

    // BACKCOMPAT: 2020.3
    @Suppress("UnstableApiUsage")
    override fun removeRow(index: Int) {
        descriptor.config.parameters.removeAt(index)
        super.removeRow(index)
    }

    /**
     * Swap order of parameters.
     */
    override fun fireTableRowsUpdated(firstRow: Int, lastRow: Int) {
        val parameters = descriptor.config.parameters
        val tmp = parameters[firstRow]
        parameters[firstRow] = parameters[lastRow]
        parameters[lastRow] = tmp

        super.fireTableRowsUpdated(firstRow, lastRow)
    }

    private fun createNewParameter(descriptor: SignatureDescriptor): Parameter =
        Parameter(factory, "p${descriptor.parametersCount}", ParameterProperty.Empty())

    private class SignatureTypeColumn(descriptor: SignatureDescriptor)
        : TypeColumn<SignatureParameter, ModelItem>(descriptor.function.project, RsFileType) {
        override fun setValue(item: ModelItem?, value: PsiCodeFragment?) {
            val fragment = value as? RsTypeReferenceCodeFragment ?: return
            if (item != null) {
                item.parameter.parameter.type = ParameterProperty.fromText(fragment.typeReference, fragment.text)
            }
        }
    }

    private class SignatureDefaultValueColumn(descriptor: SignatureDescriptor)
        : DefaultValueColumn<SignatureParameter, ModelItem>(descriptor.function.project, RsFileType) {
        override fun setValue(item: ModelItem?, value: PsiCodeFragment?) {
            val fragment = value as? RsExpressionCodeFragment ?: return
            if (item != null) {
                item.parameter.parameter.defaultValue = ParameterProperty.fromText(fragment.expr, fragment.text)
            }
        }
    }
}

private class ChangeSignatureDialog(project: Project, descriptor: SignatureDescriptor) :
    ChangeSignatureDialogBase<SignatureParameter,
        RsFunction,
        String,
        SignatureDescriptor,
        ModelItem,
        TableModel
        >(project, descriptor, false, descriptor.function) {
    private var isValid: Boolean = true

    private val config: RsChangeFunctionSignatureConfig
        get() = myMethod.config

    private var visibilityComboBox: VisibilityComboBox? = null

    override fun getFileType(): LanguageFileType = RsFileType

    override fun placeReturnTypeBeforeName(): Boolean = false

    override fun createNorthPanel(): JComponent? {
        val panel = super.createNorthPanel() ?: return null
        // Make all two (or three) elements the same size
        myNameField.setPreferredWidth(-1)
        myReturnTypeField.setPreferredWidth(-1)

        if (config.allowsVisibilityChange) {
            val visibilityPanel = JPanel(BorderLayout(0, 2))
            val visibilityLabel = JLabel("Visibility:")
            visibilityPanel.add(visibilityLabel, BorderLayout.NORTH)

            val visibility = VisibilityComboBox(project, config.visibility) { updateSignature() }
            visibilityLabel.labelFor = visibility.component
            visibilityPanel.add(visibility.component, BorderLayout.SOUTH)
            visibilityComboBox = visibility

            // Place visibility before function name and return type
            val layout = panel.layout as GridBagLayout
            val nameConstraints = layout.getConstraints(myNamePanel).clone() as GridBagConstraints
            nameConstraints.gridx = 1
            layout.setConstraints(myNamePanel, nameConstraints)

            val myReturnTypePanel = myReturnTypeField.parent
            val returnTypeConstraints = layout.getConstraints(myReturnTypePanel).clone() as GridBagConstraints
            returnTypeConstraints.gridx = 2
            layout.setConstraints(myReturnTypePanel, returnTypeConstraints)

            val gbc = GridBagConstraints(
                0, 0, 1, 1, 1.0, 1.0,
                GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL,
                Insets(0, 0, 0, 0),
                0, 0
            )
            panel.add(visibilityPanel, gbc)
        }
        return panel
    }

    override fun createSouthAdditionalPanel(): JPanel {
        val asyncBox = CheckBox("Async", config.isAsync)
        asyncBox.addChangeListener {
            config.isAsync = asyncBox.isSelected
            updateSignature()
        }
        val unsafeBox = CheckBox("Unsafe", config.isUnsafe)
        unsafeBox.addChangeListener {
            config.isUnsafe = unsafeBox.isSelected
            updateSignature()
        }

        return JPanel().apply {
            layout = MigLayout("align center center, insets 0 ${JBUI.scale(10)} 0 0")
            add(asyncBox)
            add(unsafeBox)
        }
    }

    override fun createParametersInfoModel(
        descriptor: SignatureDescriptor
    ): TableModel = TableModel(descriptor, ::updateSignature)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        RsChangeSignatureProcessor(project, config.createChangeInfo())

    override fun createReturnTypeCodeFragment(): PsiCodeFragment =
        createTypeCodeFragment(myMethod.function.createVirtualImportContext(), myMethod.function.retType?.typeReference)

    override fun createCallerChooser(
        title: String?,
        treeToReuse: Tree?,
        callback: Consumer<MutableSet<RsFunction>>?
    ): CallerChooserBase<RsFunction>? = null

    override fun validateAndCommitData(): String? {
        // Needed to update return/parameter type references
        config.function.project.rustPsiManager.incRustStructureModificationCount()
        return validateAndUpdateData()
    }

    override fun areButtonsValid(): Boolean = isValid

    override fun updateSignature() {
        updateState()
        super.updateSignature()
    }

    override fun updateSignatureAlarmFired() {
        super.updateSignatureAlarmFired()
        validateButtons()
    }

    override fun canRun() {
        val error = validateAndUpdateData()
        if (error != null) {
            throw ConfigurationException(error)
        }

        super.canRun()
    }

    /**
     * Updates the config from UI elements that are not updated automatically and also the validity state of the dialog.
     */
    private fun updateState() {
        isValid = validateAndUpdateData() == null
    }

    @Suppress("UnstableApiUsage")
    @DialogMessage
    private fun validateAndUpdateData(): String? {
        val factory = RsPsiFactory(config.function.project)

        if (myNameField != null) {
            val functionName = myNameField.text
            if (validateName(functionName)) {
                config.name = functionName
            } else return "Function name must be a valid Rust identifier"
        }

        if (myReturnTypeField != null) {
            val returnTypeText = myReturnTypeField.text
            val returnType = if (returnTypeText.isBlank()) {
                factory.createType("()")
            } else {
                (myReturnTypeCodeFragment as? RsTypeReferenceCodeFragment)?.typeReference
            }
            if (returnType != null) {
                config.returnTypeDisplay = returnType
            } else {
                return "Function return type must be a valid Rust type"
            }
        }

        val visField = visibilityComboBox
        if (visField != null) {
            if (visField.hasValidVisibility) {
                config.visibility = visField.visibility
            } else {
                return "Function visibility must be a valid visibility specifier"
            }
        }

        for ((index, parameter) in config.parameters.withIndex()) {
            if (parameter.parsePat() == null) {
                return "Parameter $index has invalid pattern"
            }
            if (parameter.type is ParameterProperty.Empty) {
                return "Please enter type for parameter $index"
            }
            if (parameter.type is ParameterProperty.Invalid) {
                return "Type entered for parameter $index is invalid"
            }
            if (parameter.defaultValue is ParameterProperty.Invalid) {
                return "Default value entered for parameter $index is invalid"
            }
        }

        return null
    }

    override fun calculateSignature(): String = config.signature()

    /**
     * This is unused, since visibility is handled with a custom input.
     */
    override fun createVisibilityControl(): ComboBoxVisibilityPanel<String> =
        object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}
}

private fun createTypeCodeFragment(
    importContext: RsMod,
    type: RsTypeReference?
): PsiCodeFragment = createCodeFragment(importContext) { importTarget ->
    RsTypeReferenceCodeFragment(
        importContext.project,
        type?.text.orEmpty(),
        context = importTarget,
        importTarget = importTarget
    )
}

private fun createExprCodeFragment(importContext: RsMod): PsiCodeFragment
    = createCodeFragment(importContext) { importTarget ->
    RsExpressionCodeFragment(
        importContext.project,
        "",
        context = importTarget,
        importTarget = importTarget
    )
}

private fun createCodeFragment(
    importContext: RsMod,
    factory: (importTarget: RsItemsOwner) -> RsCodeFragment
): PsiCodeFragment {
    val fragment = factory(importContext)
    val document = fragment.document!!
    document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            PsiDocumentManager.getInstance(importContext.project).commitDocument(document)
        }
    })
    return fragment
}

private fun validateName(name: String): Boolean = name.isNotBlank() && isValidRustVariableIdentifier(name)

private class VisibilityComboBox(project: Project, initialVis: RsVis?, onChange: () -> Unit) {
    private val combobox: ComboBox<String> = ComboBox<String>(createVisibilityHints(initialVis), 80)
    private val factory: RsPsiFactory = RsPsiFactory(project)

    val component: JComponent = combobox

    val hasValidVisibility: Boolean
        get() = (combobox.selectedItem as String).isBlank() || visibility != null
    val visibility: RsVis?
        get() = factory.tryCreateVis(combobox.selectedItem as String)

    init {
        combobox.isEditable = true
        combobox.selectedItem = initialVis?.text.orEmpty()
        combobox.addActionListener {
            onChange()
        }
    }
}

private fun createVisibilityHints(initialVis: RsVis?): Array<String> =
    setOf(initialVis?.text.orEmpty(), "", "pub", "pub(crate)", "pub(super)").toTypedArray()
