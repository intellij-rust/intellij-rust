package org.rust.ide.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable

class RsCodeFoldingOptionsProvider :
    BeanConfigurable<RsCodeFoldingSettings>(RsCodeFoldingSettings.instance),
    CodeFoldingOptionsProvider {

    init {
        val settings = instance
        val getter: () -> Boolean = { settings.collapsibleOneLineMethods }
        val setter: (Boolean) -> Unit = { v -> settings.collapsibleOneLineMethods = v }

        checkBox("Rust one-line methods", getter, setter)
    }
}
