package org.rust.lang.template

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class RustLiveTemplatesProvider : DefaultLiveTemplatesProvider{
    override fun getDefaultLiveTemplateFiles(): Array<out String>? {
        return arrayOf("/org/rust/lang/liveTemplates/Rust")
    }

    override fun getHiddenLiveTemplateFiles(): Array<out String>? = null
}
