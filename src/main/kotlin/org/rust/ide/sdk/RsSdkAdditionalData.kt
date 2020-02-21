package org.rust.ide.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import org.rust.ide.sdk.flavors.RsSdkFlavor

open class RsSdkAdditionalData(
    val flavor: RsSdkFlavor? = null,
    var associatedModulePath: String? = null
) : SdkAdditionalData {

    protected constructor(from: RsSdkAdditionalData) : this(from.flavor, from.associatedModulePath)

    fun copy(): RsSdkAdditionalData = RsSdkAdditionalData(this)

    open fun associateWithModule(module: Module) {
        module.basePath?.let { associateWithModulePath(it) }
    }

    open fun associateWithModulePath(modulePath: String) {
        associatedModulePath = FileUtil.toSystemIndependentName(modulePath)
    }

    open fun save(rootElement: Element) {
        val associatedModulePath = associatedModulePath ?: return
        rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, associatedModulePath)
    }

    protected open fun load(element: Element?) {
        if (element != null) {
            associatedModulePath = element.getAttributeValue(ASSOCIATED_PROJECT_PATH)
        }
    }

    companion object {
        private const val ASSOCIATED_PROJECT_PATH: String = "ASSOCIATED_PROJECT_PATH"

        fun load(sdk: Sdk, element: Element?): RsSdkAdditionalData {
            val data = RsSdkAdditionalData(RsSdkFlavor.getFlavor(sdk.homePath))
            data.load(element)
            return data
        }
    }
}
