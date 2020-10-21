/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.util.io.DigestUtil
import com.intellij.util.messages.Topic
import org.jdom.Element

data class RsSdkAdditionalData(
    var sdkKey: String = DigestUtil.randomToken(),
    var toolchainName: String? = null,
    var explicitPathToStdlib: String? = null
) : SdkAdditionalData {

    fun save(rootElement: Element) {
        rootElement.setAttribute(SDK_KEY, sdkKey)
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        explicitPathToStdlib?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    fun load(element: Element?) {
        if (element == null) return
        sdkKey = element.getAttributeValue(SDK_KEY)
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        explicitPathToStdlib = element.getAttributeValue(STDLIB_PATH)
    }

    fun interface Listener {
        fun sdkAdditionalDataChanged(sdk: Sdk)
    }

    companion object {
        private const val SDK_KEY: String = "SDK_KEY"
        private const val TOOLCHAIN_NAME: String = "TOOLCHAIN_NAME"
        private const val STDLIB_PATH: String = "STDLIB_PATH"

        @JvmField
        val RUST_ADDITIONAL_DATA_TOPIC: Topic<Listener> = Topic(
            "rust sdk additional data changes",
            Listener::class.java
        )
    }
}
