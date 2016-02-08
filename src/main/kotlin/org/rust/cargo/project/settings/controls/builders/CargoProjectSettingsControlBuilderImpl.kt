package org.rust.cargo.project.settings.controls.builders

import com.intellij.openapi.externalSystem.model.settings.LocationSettingType
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemSettingsControlCustomizer
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.settings.CargoProjectSettings
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class CargoProjectSettingsControlBuilderImpl(private val myInitialSettings: CargoProjectSettings) : CargoProjectSettingsControlBuilder {

    private val sdk: RustSdkType = SdkType.findInstance(RustSdkType::class.java)

    private var cargoHomeSettingType = LocationSettingType.UNKNOWN

    private val scheduler = Alarm(Alarm.ThreadToUse.SWING_THREAD)

    private var dropUseAutoImportBox: Boolean = false
    private var dropCreateEmptyContentRootDirectoriesBox: Boolean = false
    private var disabledUseLocalDistributionButton: Boolean = false
    private var disabledCargoHomePathComponents: Boolean = false

    private var cargoHomeLabel: JLabel? = null
    private var cargoHomePathField: TextFieldWithBrowseButton? = null

    private var useLocalDistributionButton: JBRadioButton? = null

    fun disableCargoHomePathComponents(): CargoProjectSettingsControlBuilder {
        disabledCargoHomePathComponents = true
        return this
    }

    fun disableUseLocalDistributionButton(): CargoProjectSettingsControlBuilder {
        disabledUseLocalDistributionButton = true
        return this
    }

    fun disableUseAutoImportBox(): CargoProjectSettingsControlBuilder {
        dropUseAutoImportBox = true
        return this
    }

    fun disableCreateEmptyContentRootDirectoriesBox(): CargoProjectSettingsControlBuilder {
        dropCreateEmptyContentRootDirectoriesBox = true
        return this
    }

    override fun showUi(show: Boolean) {
        ExternalSystemUiUtil.showUi(this, show)
    }

    override fun getExternalSystemSettingsControlCustomizer(): ExternalSystemSettingsControlCustomizer? {
        return ExternalSystemSettingsControlCustomizer(dropUseAutoImportBox, dropCreateEmptyContentRootDirectoriesBox)
    }

    override fun createAndFillControls(content: PaintAwarePanel, indentLevel: Int) {
        //
        // NOTE(kudinkin): that's a shame
        //
        var rightTimeToShowBalloon = false

        content.paintCallback = Consumer {
            if (shouldShowBalloon() && rightTimeToShowBalloon) {
                showBalloon()
                rightTimeToShowBalloon = false;
            }
        }

        content.addPropertyChangeListener(PropertyChangeListener { e ->
            if ("ancestor" != e.propertyName) {
                return@PropertyChangeListener
            }

            // Show balloon on the first drawing only
            rightTimeToShowBalloon = e.newValue != null && e.oldValue == null

            if (e.newValue == null && e.oldValue != null) {
                // Cancel balloons in the case configurable is hidden
                scheduler.cancelAllRequests()
            }
        })

        addChooserComponents    (content, indentLevel)
        addCargoHomeComponents  (content, indentLevel)
    }

    private fun shouldShowBalloon(): Boolean =
        cargoHomePathField?.let { it.isEnabled } ?: false

    override fun disposeUIResources() {
        ExternalSystemUiUtil.disposeUi(this)
    }

    override fun addChooserComponents(content: PaintAwarePanel, indentLevel: Int): CargoProjectSettingsControlBuilderImpl {
        if (disabledUseLocalDistributionButton)
            return this;

        val buttonGroup = ButtonGroup()

        val button = JBRadioButton("Use local distribution")

        button.addActionListener(ActionListener {
            cargoHomePathField?.let { pathField ->
                val local = useLocalDistributionButton?.let { it.isSelected } ?: false

                pathField.isEnabled = local

                if (local) {
                    if (pathField.text.isEmpty()) {
                        tryFindCargoHome()
                    } else {
                        if (isValidCargoHome(pathField.text)) {
                            cargoHomeSettingType = LocationSettingType.EXPLICIT_CORRECT
                        } else {
                            cargoHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT
                        }
                    }

                    showBalloon()
                } else {
                    scheduler.cancelAllRequests()
                }
            }
        })

        buttonGroup.add(button)

        content.add(button, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))

        // Selected by default?
        button.isSelected = true;

        useLocalDistributionButton = button

        return this
    }

    override fun addCargoHomeComponents(content: PaintAwarePanel, indentLevel: Int): CargoProjectSettingsControlBuilder {
        if (disabledCargoHomePathComponents)
            return this

        val pathField = TextFieldWithBrowseButton()

        pathField.let { field ->
            field.addBrowseFolderListener(
                "",
                "Cargo home",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                false)

            field.textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    cargoHomePathField!!.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
                }

                override fun removeUpdate(e: DocumentEvent) {
                    cargoHomePathField!!.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
                }

                override fun changedUpdate(e: DocumentEvent) {}
            })
        }

        cargoHomeLabel      = JBLabel("Cargo home")
        cargoHomePathField  = pathField

        content.add(cargoHomeLabel,       ExternalSystemUiUtil.getLabelConstraints(indentLevel))
        content.add(cargoHomePathField,   ExternalSystemUiUtil.getFillLineConstraints(0))

        return this
    }

    @Throws(ConfigurationException::class)
    override fun validate(settings: CargoProjectSettings): Boolean {
        cargoHomePathField?.let { pathField ->
            val cargoHomePath = FileUtil.toCanonicalPath(pathField.text)

            useLocalDistributionButton?.let { button ->
                if (button.isSelected) {
                    if (StringUtil.isEmpty(cargoHomePath)) {
                        cargoHomeSettingType = LocationSettingType.UNKNOWN
                        throw ConfigurationException("Cargo binary location is not specified!")
                    } else if (!isValidCargoHome(cargoHomePath)) {
                        cargoHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT
                        showBalloon(MessageType.ERROR, cargoHomeSettingType)
                        throw ConfigurationException("Cargo binary not found at: $cargoHomePath!")
                    }
                }
            }
        }

        return true
    }

    override fun apply(settings: CargoProjectSettings) {
        cargoHomePathField?.let { pathField ->
            val cargoHomePath = FileUtil.toCanonicalPath(pathField.text)
            if (StringUtil.isEmpty(cargoHomePath)) {
                settings.cargoHome = null
            } else {
                settings.cargoHome = adjustCargoHome(cargoHomePath)
            }
        }

        useLocalDistributionButton?.let { button ->
            if (button.isSelected) {
                settings.distributionType = CargoProjectSettings.Companion.Distribution.LOCAL
            }
        }
    }

    override fun reset(project: Project?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean) {
        val cargoHome = settings.cargoHome  ?: ""

        cargoHomePathField?.let { pathField ->
            pathField.text = cargoHome
            pathField.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
        }

        useLocalDistributionButton?.let { button ->
            if (!button.isSelected) {
                cargoHomePathField!!.isEnabled = false
                return
            }
        }

        if (StringUtil.isEmpty(cargoHome)) {
            cargoHomeSettingType = LocationSettingType.UNKNOWN
            tryFindCargoHome()
        } else {
            cargoHomeSettingType =
                if (isValidCargoHome(cargoHome))
                    LocationSettingType.EXPLICIT_CORRECT
                else
                    LocationSettingType.EXPLICIT_INCORRECT

            scheduler.cancelAllRequests()

            if (cargoHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT && settings.distributionType === CargoProjectSettings.Companion.Distribution.LOCAL) {
                showBalloon(MessageType.ERROR, cargoHomeSettingType)
            }
        }
    }

    override fun update(linkedProjectPath: String?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean) {
        // NOP
    }

    private fun showBalloon() {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (cargoHomeSettingType) {
            LocationSettingType.EXPLICIT_INCORRECT, LocationSettingType.UNKNOWN -> showBalloon(MessageType.ERROR, cargoHomeSettingType)
            LocationSettingType.DEDUCED                                         -> showBalloon(MessageType.INFO, cargoHomeSettingType)
        }
    }

    private fun showBalloon(messageType: MessageType, settingType: LocationSettingType) {
        ExternalSystemUiUtil.showBalloon(cargoHomePathField!!, messageType, settingType.getDescription(CargoConstants.PROJECT_SYSTEM_ID))
    }

    override fun getInitialSettings(): CargoProjectSettings {
        return myInitialSettings
    }

    private fun isValidCargoHome(cargoHome: String): Boolean =
        sdk.isValidCargoHome(cargoHome) || sdk.adjustSelectedSdkHome(cargoHome).let { sdk.isValidCargoHome(it) }

    private fun adjustCargoHome(cargoHome: String): String {
        if (sdk.isValidCargoHome(cargoHome))
            return cargoHome

        return sdk.adjustSelectedSdkHome(cargoHome)
    }

    private fun tryFindCargoHome() {
        cargoHomePathField?.let { pathField ->
            val cargoHome = sdk.suggestHomePath()
            if (cargoHome == null) {
                showBalloon(MessageType.WARNING, LocationSettingType.UNKNOWN)
                return
            }

            cargoHomeSettingType = LocationSettingType.DEDUCED

            showBalloon(MessageType.INFO, LocationSettingType.DEDUCED)

            pathField.text                  = cargoHome
            pathField.textField.foreground  = LocationSettingType.DEDUCED.color
        }
    }

    override fun isModified(): Boolean {
        val distributionType = myInitialSettings.distributionType

        useLocalDistributionButton?.let { button ->
            if (button.isSelected && distributionType !== CargoProjectSettings.Companion.Distribution.LOCAL)
                return true
        }

        return cargoHomePathField?.let { pathField ->
            val cargoHome = FileUtil.toCanonicalPath(pathField.text)
            if (StringUtil.isEmpty(cargoHome)) {
                !StringUtil.isEmpty(myInitialSettings.cargoHome)
            } else {
                cargoHome != myInitialSettings.cargoHome
            }
        } ?: false;
    }
}
