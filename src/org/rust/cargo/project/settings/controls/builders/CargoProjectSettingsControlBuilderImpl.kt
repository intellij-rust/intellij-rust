package org.rust.cargo.project.settings.controls.builders

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemSettingsControlCustomizer
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import org.rust.cargo.Cargo
import org.rust.cargo.project.settings.CargoProjectSettings
import org.rust.cargo.service.CargoInstallationManager
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class CargoProjectSettingsControlBuilderImpl(private val myInitialSettings: CargoProjectSettings) : CargoProjectSettingsControlBuilder {

    private val myInstallationManager: CargoInstallationManager
    private val myAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private var myCargoHomeSettingType = LocationSettingType.UNKNOWN

    private var myShowBalloonIfNecessary: Boolean = false
    private val myActionListener: ActionListener

    private var dropUseAutoImportBox: Boolean = false
    private var dropCreateEmptyContentRootDirectoriesBox: Boolean = false

    @SuppressWarnings("FieldCanBeLocal")
    private var myCargoHomeLabel: JLabel? = null
    private var myCargoHomePathField: TextFieldWithBrowseButton? = null
    private var dropCargoHomePathComponents: Boolean = false

    private var myUseLocalDistributionButton: JBRadioButton? = null
    private var dropUseLocalDistributionButton: Boolean = false

    init {
        myInstallationManager = ServiceManager.getService(CargoInstallationManager::class.java)

        myActionListener = ActionListener {
            if (myCargoHomePathField == null) return@ActionListener

            val localDistributionEnabled = myUseLocalDistributionButton != null && myUseLocalDistributionButton!!.isSelected
            myCargoHomePathField!!.isEnabled = localDistributionEnabled
            if (localDistributionEnabled) {
                if (myCargoHomePathField!!.text.isEmpty()) {
                    deduceCargoHomeIfPossible()
                } else {
                    if (myInstallationManager.isCargoSDK(myCargoHomePathField!!.text)) {
                        myCargoHomeSettingType = LocationSettingType.EXPLICIT_CORRECT
                    } else {
                        myCargoHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT
                        myShowBalloonIfNecessary = true
                    }
                }
                showBalloonIfNecessary()
            } else {
                myAlarm.cancelAllRequests()
            }
        }
    }

    fun dropGradleHomePathComponents(): CargoProjectSettingsControlBuilder {
        dropCargoHomePathComponents = true
        return this
    }

    fun dropUseLocalDistributionButton(): CargoProjectSettingsControlBuilder {
        dropUseLocalDistributionButton = true
        return this
    }

    fun dropUseAutoImportBox(): CargoProjectSettingsControlBuilder {
        dropUseAutoImportBox = true
        return this
    }

    fun dropCreateEmptyContentRootDirectoriesBox(): CargoProjectSettingsControlBuilder {
        dropCreateEmptyContentRootDirectoriesBox = true
        return this
    }

    override fun showUi(show: Boolean) {
        ExternalSystemUiUtil.showUi(this, show)
    }

    override fun getInitialSettings(): CargoProjectSettings {
        return myInitialSettings
    }

    override fun getExternalSystemSettingsControlCustomizer(): ExternalSystemSettingsControlCustomizer? {
        return ExternalSystemSettingsControlCustomizer(dropUseAutoImportBox, dropCreateEmptyContentRootDirectoriesBox)
    }

    override fun createAndFillControls(content: PaintAwarePanel, indentLevel: Int) {
        content.paintCallback = Consumer { showBalloonIfNecessary() }

        content.addPropertyChangeListener(PropertyChangeListener { evt ->
            if ("ancestor" != evt.propertyName) {
                return@PropertyChangeListener
            }

            // Configure the balloon to show on initial configurable drawing.
            myShowBalloonIfNecessary = evt.newValue != null && evt.oldValue == null

            if (evt.newValue == null && evt.oldValue != null) {
                // Cancel delayed balloons when the configurable is hidden.
                myAlarm.cancelAllRequests()
            }
        })

        addChooserComponents(content, indentLevel)
        addGradleHomeComponents(content, indentLevel)
    }

    override fun disposeUIResources() {
        ExternalSystemUiUtil.disposeUi(this)
    }

    private fun deduceCargoHomeIfPossible() {
        if (myCargoHomePathField == null) return

        val cargoHome = myInstallationManager.tryFindCargoHome()
        if (cargoHome == null) {
            showBalloon(MessageType.WARNING, LocationSettingType.UNKNOWN)
            return
        }

        myCargoHomeSettingType = LocationSettingType.DEDUCED
        showBalloon(MessageType.INFO, LocationSettingType.DEDUCED)
        myCargoHomePathField!!.text = cargoHome.path
        myCargoHomePathField!!.textField.foreground = LocationSettingType.DEDUCED.color
    }

    override fun addChooserComponents(content: PaintAwarePanel, indentLevel: Int): CargoProjectSettingsControlBuilderImpl {
        val buttonGroup = ButtonGroup()

        if (!dropUseLocalDistributionButton) {
            myUseLocalDistributionButton = JBRadioButton("Use local distribution")
            myUseLocalDistributionButton!!.addActionListener(myActionListener)
            buttonGroup.add(myUseLocalDistributionButton)
            content.add(myUseLocalDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
        }

        return this
    }

    @Throws(ConfigurationException::class)
    override fun validate(settings: CargoProjectSettings): Boolean {
        if (myCargoHomePathField == null)
            return true

        val cargoHomePath = FileUtil.toCanonicalPath(myCargoHomePathField!!.text)

        if (myUseLocalDistributionButton != null && myUseLocalDistributionButton!!.isSelected) {
            if (StringUtil.isEmpty(cargoHomePath)) {
                myCargoHomeSettingType = LocationSettingType.UNKNOWN
                throw ConfigurationException("Cargo binary location is not specified")
            } else if (!myInstallationManager.isCargoSDK(cargoHomePath)) {
                myCargoHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT
                showBalloon(MessageType.ERROR, myCargoHomeSettingType)
                throw ConfigurationException("Cargo binary not found at: {0}", cargoHomePath)
            }
        }
        return true
    }

    override fun apply(settings: CargoProjectSettings) {
        if (myCargoHomePathField != null) {
            val cargoHomePath = FileUtil.toCanonicalPath(myCargoHomePathField!!.text)
            if (StringUtil.isEmpty(cargoHomePath)) {
                settings.setCargoHome(null)
            } else {
                settings.setCargoHome(cargoHomePath)
            }
        }

        if (myUseLocalDistributionButton != null && myUseLocalDistributionButton!!.isSelected) {
            settings.setDistributionType(CargoProjectSettings.Companion.Distribution.LOCAL)
        }
    }

    override fun isModified(): Boolean {
        val distributionType = myInitialSettings.getDistributionType()

        if (myUseLocalDistributionButton != null && myUseLocalDistributionButton!!.isSelected && distributionType !== CargoProjectSettings.Companion.Distribution.LOCAL) {
            return true
        }

        if (myCargoHomePathField == null) return false
        val cargoHome = FileUtil.toCanonicalPath(myCargoHomePathField!!.text)
        if (StringUtil.isEmpty(cargoHome)) {
            return !StringUtil.isEmpty(myInitialSettings.getCargoHome())
        } else {
            return cargoHome != myInitialSettings.getCargoHome()
        }
    }

    override fun reset(project: Project?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean) {
        val cargoHome = settings.getCargoHome()
        if (myCargoHomePathField != null) {
            myCargoHomePathField!!.text = cargoHome ?: ""
            myCargoHomePathField!!.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
        }

        if (myUseLocalDistributionButton != null && !myUseLocalDistributionButton!!.isSelected) {
            myCargoHomePathField!!.isEnabled = false
            return
        }

        if (StringUtil.isEmpty(cargoHome)) {
            myCargoHomeSettingType = LocationSettingType.UNKNOWN
            deduceCargoHomeIfPossible()
        } else {
            myCargoHomeSettingType = if (myInstallationManager.isCargoSDK(cargoHome))
                LocationSettingType.EXPLICIT_CORRECT
            else
                LocationSettingType.EXPLICIT_INCORRECT
            myAlarm.cancelAllRequests()
            if (myCargoHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT && settings.getDistributionType() === CargoProjectSettings.Companion.Distribution.LOCAL) {
                showBalloon(MessageType.ERROR, myCargoHomeSettingType)
            }
        }
    }

    override fun update(linkedProjectPath: String?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean) { }

    override fun addGradleHomeComponents(content: PaintAwarePanel, indentLevel: Int): CargoProjectSettingsControlBuilder {
        if (dropCargoHomePathComponents) return this

        myCargoHomeLabel = JBLabel("Cargo home")
        myCargoHomePathField = TextFieldWithBrowseButton()

        myCargoHomePathField!!.addBrowseFolderListener(
                "",
                "Cargo home",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                false)
        myCargoHomePathField!!.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                myCargoHomePathField!!.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
            }

            override fun removeUpdate(e: DocumentEvent) {
                myCargoHomePathField!!.textField.foreground = LocationSettingType.EXPLICIT_CORRECT.color
            }

            override fun changedUpdate(e: DocumentEvent) {
            }
        })

        content.add(myCargoHomeLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel))
        content.add(myCargoHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0))

        return this
    }

    internal fun showBalloonIfNecessary() {
        if (!myShowBalloonIfNecessary || (myCargoHomePathField != null && !myCargoHomePathField!!.isEnabled)) {
            return
        }
        myShowBalloonIfNecessary = false
        var messageType: MessageType? = null
        when (myCargoHomeSettingType) {
            LocationSettingType.DEDUCED                                         -> messageType = MessageType.INFO
            LocationSettingType.EXPLICIT_INCORRECT, LocationSettingType.UNKNOWN -> messageType = MessageType.ERROR
        }
        if (messageType != null) {
            showBalloon(messageType, myCargoHomeSettingType)
        }
    }

    private fun showBalloon(messageType: MessageType, settingType: LocationSettingType) {
        ExternalSystemUiUtil.showBalloon(myCargoHomePathField!!, messageType, settingType.getDescription(Cargo.PROJECT_SYSTEM_ID))
    }
}
