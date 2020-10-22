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

open class RsSdkAdditionalData(
    var sdkKey: String = DigestUtil.randomToken(),
    var toolchainName: String? = null,
    var explicitPathToStdlib: String? = null
) : SdkAdditionalData {

    open fun save(rootElement: Element) {
        rootElement.setAttribute(SDK_KEY, sdkKey)
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        explicitPathToStdlib?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    open fun load(element: Element?) {
        if (element == null) return
        sdkKey = element.getAttributeValue(SDK_KEY)
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        explicitPathToStdlib = element.getAttributeValue(STDLIB_PATH)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RsSdkAdditionalData

        if (sdkKey != other.sdkKey) return false
        if (toolchainName != other.toolchainName) return false
        if (explicitPathToStdlib != other.explicitPathToStdlib) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sdkKey.hashCode()
        result = 31 * result + (toolchainName?.hashCode() ?: 0)
        result = 31 * result + (explicitPathToStdlib?.hashCode() ?: 0)
        return result
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

        fun load(element: Element?): RsSdkAdditionalData =
            RsSdkAdditionalData().apply { load(element) }
    }
}
