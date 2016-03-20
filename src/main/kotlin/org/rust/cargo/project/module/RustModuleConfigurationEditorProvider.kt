package org.rust.cargo.project.module

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState

class RustModuleConfigurationEditorProvider: ModuleConfigurationEditorProvider {
    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor>? {
        val editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
        return arrayOf(
            editorFactory.createClasspathEditor(state),
            editorFactory.createModuleContentRootsEditor(state)
        )
    }
}
