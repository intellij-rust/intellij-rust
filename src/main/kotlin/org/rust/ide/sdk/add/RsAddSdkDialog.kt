package org.rust.ide.sdk.add

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.messages.showProcessExecutionErrorDialog
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ExceptionUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.rust.ide.sdk.RsSdkType
import org.rust.ide.sdk.RsSdkUtils
import org.rust.stdext.RsExecutionException
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

class RsAddSdkDialog private constructor(
    private val project: Project?,
    private val module: Module?,
    private val existingSdks: List<Sdk>
) : DialogWrapper(project) {
    private val mainPanel: JPanel = JPanel(JBCardLayout())
    private var selectedPanel: RsAddSdkView? = null
    private val context: UserDataHolderBase = UserDataHolderBase()
    private var panels: List<RsAddSdkView> = emptyList()

    init {
        title = "Add Rust Toolchain"
    }

    override fun createCenterPanel(): JComponent {
        val sdks = existingSdks.filter { it.sdkType is RsSdkType && !RsSdkUtils.isInvalid(it) }
        val panels = createPanels(sdks).toMutableList()
        val extendedPanels = RsAddSdkProvider.EP_NAME.extensions
            .mapNotNull {
                it.safeCreateView(
                    project = project,
                    module = module,
                    existingSdks = existingSdks,
                    context = context
                ).registerIfDisposable()
            }
        panels.addAll(extendedPanels)
        mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels))
        return mainPanel
    }

    private fun createPanels(sdks: List<Sdk>): List<RsAddSdkView> =
        listOf(RsAddRustupPanel(), RsAddCargoPanel())

    private fun <T> T.registerIfDisposable(): T = apply { (this as? Disposable)?.let { Disposer.register(disposable, it) } }

    private var navigationPanelCardLayout: CardLayout? = null

    private var southPanel: JPanel? = null

    override fun createSouthPanel(): JComponent {
        val regularDialogSouthPanel = super.createSouthPanel()
        val wizardDialogSouthPanel = createWizardSouthPanel()

        navigationPanelCardLayout = CardLayout()

        val result = JPanel(navigationPanelCardLayout).apply {
            add(regularDialogSouthPanel, REGULAR_CARD_PANE)
            add(wizardDialogSouthPanel, WIZARD_CARD_PANE)
        }

        southPanel = result

        return result
    }

    private fun createWizardSouthPanel(): JPanel {
        assert(value = style != DialogStyle.COMPACT,
            lazyMessage = { "${RsAddSdkDialog::class.java} is not ready for ${DialogStyle.COMPACT} dialog style" })

        return doCreateSouthPanel(
            leftButtons = listOf(),
            rightButtons = listOf(previousButton.value, nextButton.value, cancelButton.value)
        )
    }

    @Suppress("SuspiciousPackagePrivateAccess")
    private val nextAction: Action = object : DialogWrapperAction("Next") {
        override fun doAction(e: ActionEvent) {
            selectedPanel?.let {
                if (it.actions.containsKey(RsAddSdkDialogFlowAction.NEXT)) onNext()
                else if (it.actions.containsKey(RsAddSdkDialogFlowAction.FINISH)) {
                    onFinish()
                }
            }
        }
    }

    private val nextButton = lazy { createJButtonForAction(nextAction) }

    @Suppress("SuspiciousPackagePrivateAccess")
    private val previousAction: DialogWrapperAction = object : DialogWrapperAction("Previous") {
        override fun doAction(e: ActionEvent) = onPrevious()
    }

    private val previousButton: Lazy<JButton> = lazy { createJButtonForAction(previousAction) }

    private val cancelButton: Lazy<JButton> = lazy { createJButtonForAction(cancelAction) }

    override fun postponeValidation(): Boolean = false

    override fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll().orEmpty()

    fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

    private fun createCardSplitter(panels: List<RsAddSdkView>): Splitter {
        this.panels = panels
        return Splitter(false, 0.25f).apply {
            val cardLayout = CardLayout()
            val cardPanel = JPanel(cardLayout).apply {
                preferredSize = JBUI.size(640, 480)
                for (panel in panels) {
                    add(panel.component, panel.panelName)

                    panel.addStateListener(object : RsAddSdkStateListener {
                        override fun onComponentChanged() {
                            show(mainPanel, panel.component)

                            selectedPanel?.let { updateWizardActionButtons(it) }
                        }

                        override fun onActionsStateChanged() {
                            selectedPanel?.let { updateWizardActionButtons(it) }
                        }
                    })
                }
            }
            val cardsList = JBList(panels).apply {
                val descriptor = object : ListItemDescriptorAdapter<RsAddSdkView>() {
                    override fun getTextFor(value: RsAddSdkView) = StringUtil.toTitleCase(value.panelName)
                    override fun getIconFor(value: RsAddSdkView) = value.icon
                }
                cellRenderer = object : GroupedItemsListRenderer<RsAddSdkView>(descriptor) {
                    override fun createItemComponent() = super.createItemComponent().apply {
                        border = JBUI.Borders.empty(4, 4, 4, 10)
                    }
                }
                addListSelectionListener {
                    selectedPanel = selectedValue
                    cardLayout.show(cardPanel, selectedValue.panelName)

                    southPanel?.let {
                        if (selectedValue.actions.containsKey(RsAddSdkDialogFlowAction.NEXT)) {
                            navigationPanelCardLayout?.show(it, WIZARD_CARD_PANE)
                            rootPane.defaultButton = nextButton.value

                            updateWizardActionButtons(selectedValue)
                        } else {
                            navigationPanelCardLayout?.show(it, REGULAR_CARD_PANE)
                            rootPane.defaultButton = getButton(okAction)
                        }
                    }

                    selectedValue.onSelected()
                }
                selectedPanel = panels.getOrNull(0)
                selectedIndex = 0
            }

            firstComponent = cardsList
            secondComponent = cardPanel
        }
    }

    private fun onNext() {
        selectedPanel?.let {
            it.next()

            // sliding effect
            swipe(mainPanel, it.component, JBCardLayout.SwipeDirection.FORWARD)

            updateWizardActionButtons(it)
        }
    }

    private fun onPrevious() {
        selectedPanel?.let {
            it.previous()

            // sliding effect
            if (it.actions.containsKey(RsAddSdkDialogFlowAction.PREVIOUS)) {
                val stepContent = it.component
                val stepContentName = stepContent.hashCode().toString()

                (mainPanel.layout as JBCardLayout).swipe(mainPanel, stepContentName, JBCardLayout.SwipeDirection.BACKWARD)
            } else {
                // this is the first wizard step
                (mainPanel.layout as JBCardLayout).swipe(mainPanel, SPLITTER_COMPONENT_CARD_PANE, JBCardLayout.SwipeDirection.BACKWARD)
            }

            updateWizardActionButtons(it)
        }
    }

    override fun doOKAction() {
        try {
            selectedPanel?.complete()
        } catch (e: CreateSdkInterrupted) {
            return
        } catch (e: Exception) {
            val cause = ExceptionUtil.findCause(e, RsExecutionException::class.java)
            if (cause == null) {
                Messages.showErrorDialog(e.localizedMessage, "Error")
            } else {
                showProcessExecutionErrorDialog(project, cause)
            }
            return
        }

        close(OK_EXIT_CODE)
    }

    private fun onFinish() {
        doOKAction()
    }

    private fun updateWizardActionButtons(it: RsAddSdkView) {
        previousButton.value.isEnabled = false

        it.actions.forEach { (action, isEnabled) ->
            val actionButton = when (action) {
                RsAddSdkDialogFlowAction.PREVIOUS -> previousButton.value
                RsAddSdkDialogFlowAction.NEXT -> nextButton.value.apply { text = "Next" }
                RsAddSdkDialogFlowAction.FINISH -> nextButton.value.apply { text = "Finish" }
                else -> null
            }
            actionButton?.isEnabled = isEnabled
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RsAddSdkDialog::class.java)

        private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"

        private const val REGULAR_CARD_PANE = "Regular"

        private const val WIZARD_CARD_PANE = "Wizard"

        fun show(project: Project?, module: Module?, existingSdks: List<Sdk>, sdkAddedCallback: (Sdk?) -> Unit) {
            val dialog = RsAddSdkDialog(project = project, module = module, existingSdks = existingSdks)
            dialog.init()

            val sdk = if (dialog.showAndGet()) dialog.getOrCreateSdk() else null
            sdkAddedCallback(sdk)
        }

        private fun RsAddSdkProvider.safeCreateView(
            project: Project?,
            module: Module?,
            existingSdks: List<Sdk>,
            context: UserDataHolder
        ): RsAddSdkView? = try {
            createView(project, module, null, existingSdks, context)
        } catch (e: NoClassDefFoundError) {
            LOG.info(e)
            null
        }
    }
}

class CreateSdkInterrupted : Exception()

private fun doCreateSouthPanel(leftButtons: List<JButton>, rightButtons: List<JButton>): JPanel {
    val panel = JPanel(BorderLayout())
    val insets = if (SystemInfo.isMacOSLeopard) {
        if (UIUtil.isUnderIntelliJLaF()) {
            JBUI.insets(0, 8)
        } else {
            JBUI.emptyInsets()
        }
    } else if (UIUtil.isUnderWin10LookAndFeel()) {
        JBUI.emptyInsets()
    } else {
        Insets(8, 0, 0, 0) // don't wrap to JBInsets
    }

    val bag = GridBag().setDefaultInsets(insets)
    val lrButtonsPanel = NonOpaquePanel(GridBagLayout())
    val leftButtonsPanel = createButtonsPanel(leftButtons)
    leftButtonsPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 20)  // leave some space between button groups
    lrButtonsPanel.add(leftButtonsPanel, bag.next())
    lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1.0).fillCellHorizontally())   // left strut
    val buttonsPanel = createButtonsPanel(rightButtons)
    lrButtonsPanel.add(buttonsPanel, bag.next())
    panel.add(lrButtonsPanel, BorderLayout.CENTER)
    panel.border = JBUI.Borders.emptyTop(8)
    return panel
}

private fun createButtonsPanel(buttons: List<JButton>): JPanel {
    val hgap = if (SystemInfo.isMacOSLeopard) if (UIUtil.isUnderIntelliJLaF()) 8 else 0 else 5
    val buttonsPanel = NonOpaquePanel(GridLayout(1, buttons.size, hgap, 0))
    buttons.forEach { buttonsPanel.add(it) }
    return buttonsPanel
}

private fun swipe(panel: JPanel, stepContent: Component, swipeDirection: JBCardLayout.SwipeDirection) {
    val stepContentName = stepContent.hashCode().toString()
    panel.add(stepContentName, stepContent)
    (panel.layout as JBCardLayout).swipe(panel, stepContentName, swipeDirection)
}

private fun show(panel: JPanel, stepContent: Component) {
    val stepContentName = stepContent.hashCode().toString()
    panel.add(stepContentName, stepContent)
    (panel.layout as CardLayout).show(panel, stepContentName)
}

fun showProcessExecutionErrorDialog(project: Project?, e: RsExecutionException) =
    showProcessExecutionErrorDialog(project, e.localizedMessage.orEmpty(), e.command, e.stdout, e.stderr, e.exitCode)
