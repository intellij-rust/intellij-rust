package org.rust.ide.core.modules

import com.intellij.openapi.module.ModuleComponent

abstract class AbstractModuleComponent(private val name: String) : ModuleComponent {

    override fun getComponentName(): String = name

    override fun disposeComponent() {}

    override fun initComponent() {}

    override fun projectClosed() {}

    override fun projectOpened() {}

    override fun moduleAdded() {}
}

