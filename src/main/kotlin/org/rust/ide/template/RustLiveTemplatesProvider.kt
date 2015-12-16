package org.rust.ide.template

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class RustLiveTemplatesProvider : DefaultLiveTemplatesProvider{
    override fun getDefaultLiveTemplateFiles(): Array<out String>? {
        return arrayOf("/org/rust/ide/liveTemplates/Rust")
    }

    override fun getHiddenLiveTemplateFiles(): Array<out String>? = null
}
