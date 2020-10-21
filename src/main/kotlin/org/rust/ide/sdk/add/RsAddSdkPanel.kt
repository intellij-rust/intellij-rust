/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.Disposable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JPanel

/**
 * Represents the view for adding new Rust SDK. It is used in [RsAddSdkDialog].
 */
abstract class RsAddSdkPanel : JPanel(), Disposable {
    abstract val panelName: String
        @Nls(capitalization = Nls.Capitalization.Title) get

    abstract val icon: Icon

    /**
     * Returns the created sdk after closing [RsAddSdkDialog]. The method may
     * return `null` if the dialog was closed or cancelled or if the creation
     * failed.
     */
    abstract fun getOrCreateSdk(): Sdk?

    /**
     * Returns the list of validation errors. The returned list is empty if there
     * are no errors found.
     *
     * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
     */
    abstract fun validateAll(): List<ValidationInfo>
}
