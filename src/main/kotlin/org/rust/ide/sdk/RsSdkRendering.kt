/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.LayeredIcon
import org.rust.ide.sdk.remote.RsRemoteSdkUtils.hasInvalidRemoteCredentials
import org.rust.ide.sdk.remote.RsRemoteSdkUtils.isIncompleteRemote
import javax.swing.Icon

data class SdkName(val primary: String, val secondary: String?, val modifier: String?)

object RsSdkRenderingUtils {
    const val noToolchainMarker: String = "<No toolchain>"

    fun name(sdk: Sdk, sdkModificator: SdkModificator? = null): SdkName =
        name(sdk, sdkModificator?.name ?: sdk.name, sdkModificator?.versionString ?: sdk.versionString)

    /**
     * Returns modifier that shortly describes that is wrong with passed [sdk], [name] and additional info.
     */
    fun name(sdk: Sdk, name: String, version: String? = sdk.versionString): SdkName {
        val modifier = when {
            RsSdkValidator.isInvalid(sdk) || hasInvalidRemoteCredentials(sdk) -> "invalid"
            isIncompleteRemote(sdk) -> "incomplete"
            else -> null
        }
        return SdkName(name, version, modifier)
    }

    /**
     * Returns a path to be rendered as the sdk's path.
     *
     * Initial value is taken from the [sdkModificator] or the [sdk] itself,
     * then it is converted to a path relative to the user home directory.
     *
     * Returns null if the initial path or the relative value are presented in the sdk's name.
     *
     * @see FileUtil.getLocationRelativeToUserHome
     */
    fun path(sdk: Sdk, sdkModificator: SdkModificator? = null): String? {
        val name = sdkModificator?.name ?: sdk.name
        val homePath = sdkModificator?.homePath ?: sdk.homePath ?: return null
        return FileUtil.getLocationRelativeToUserHome(homePath).takeIf { homePath !in name && it !in name }
    }

    fun icon(sdk: Sdk): Icon? {
        val icon = (sdk.sdkType as? SdkType)?.icon ?: return null
        return when {
            RsSdkValidator.isInvalid(sdk)
                || isIncompleteRemote(sdk)
                || hasInvalidRemoteCredentials(sdk) -> wrapIconWithWarningDecorator(icon)
            sdk is RsDetectedSdk -> IconLoader.getTransparentIcon(icon)
            else -> icon
        }
    }

    private fun wrapIconWithWarningDecorator(icon: Icon): Icon =
        LayeredIcon.create(icon, AllIcons.Actions.Cancel)
}
