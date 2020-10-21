/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.util.Key
import com.intellij.util.Consumer
import org.rust.ide.sdk.add.RsAddSdkDialog
import java.lang.ref.WeakReference
import javax.swing.JComponent

/**
 * Class should be final and singleton since some code checks its instance by ref.
 */
class RsSdkType : RsSdkTypeBase() {
    override fun showCustomCreateUI(
        sdkModel: SdkModel,
        parentComponent: JComponent,
        selectedSdk: Sdk?,
        sdkCreatedCallback: Consumer<Sdk>
    ) {
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
        RsAddSdkDialog.show(project, sdkModel.sdks.toList()) { sdk ->
            if (sdk != null) {
                sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, WeakReference(parentComponent))
                sdkCreatedCallback.consume(sdk)
            }
        }
    }

    companion object {
        private val SDK_CREATOR_COMPONENT_KEY: Key<WeakReference<JComponent>> =
            Key.create("#org.rust.ide.sdk.creatorComponent")

        fun getInstance(): RsSdkType = findInstance(RsSdkType::class.java)
    }
}
