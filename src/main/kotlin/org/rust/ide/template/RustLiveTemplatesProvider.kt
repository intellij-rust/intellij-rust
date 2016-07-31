package org.rust.ide.template

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class RustLiveTemplatesProvider : DefaultLiveTemplatesProvider{
    override fun getDefaultLiveTemplateFiles(): Array<out String>? = arrayOf(
        "/org/rust/ide/liveTemplates/iterations",
        "/org/rust/ide/liveTemplates/output",
        "/org/rust/ide/liveTemplates/test",
        "/org/rust/ide/liveTemplates/other"
    )

    override fun getHiddenLiveTemplateFiles(): Array<out String>? = null
}
