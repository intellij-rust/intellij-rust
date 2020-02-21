package org.rust.ide.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.LayeredIcon
import org.rust.ide.sdk.flavors.RsSdkFlavor
import javax.swing.Icon

const val noToolchainMarker: String = "<No toolchain>"

data class SdkName(val primary: String, val secondary: String?, val modifier: String?)

fun name(sdk: Sdk, sdkModificator: SdkModificator? = null): SdkName =
    name(sdk, sdkModificator?.name ?: sdk.name)

fun name(sdk: Sdk, name: String): SdkName {
    val modifier = if (RsSdkUtils.isInvalid(sdk)) "invalid" else null
    val secondary = sdk.versionString
    return SdkName(name, secondary, modifier)
}

fun path(sdk: Sdk, sdkModificator: SdkModificator? = null): String? {
    val name = sdkModificator?.name ?: sdk.name
    val homePath = sdkModificator?.homePath ?: sdk.homePath ?: return null
    return FileUtil.getLocationRelativeToUserHome(homePath).takeIf { homePath !in name && it !in name }
}

fun icon(sdk: Sdk): Icon? {
    val flavor = RsSdkFlavor.getFlavor(sdk.homePath)
    val icon = flavor?.icon ?: (sdk.sdkType as? SdkType)?.icon ?: return null

    return when {
        RsSdkUtils.isInvalid(sdk) -> wrapIconWithWarningDecorator(icon)
        sdk is RsDetectedSdk -> IconLoader.getTransparentIcon(icon)
        else -> icon
    }
}

private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
    LayeredIcon(2).apply {
        setIcon(icon, 0)
        setIcon(AllIcons.Actions.Cancel, 1)
    }
