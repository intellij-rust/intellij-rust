package org.toml.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile

public object TomlFileType : LanguageFileType(TomlLanguage) {
    public object DEFAULTS {
        val EXTENSION   = "toml";
        val DESCRIPTION = "TOML file";
    }

    override fun getName()              = DEFAULTS.DESCRIPTION
    override fun getDescription()       = DEFAULTS.DESCRIPTION
    override fun getDefaultExtension()  = DEFAULTS.EXTENSION

    override fun getIcon() = AllIcons.FileTypes.Text

    override fun getCharset(file: VirtualFile, content: ByteArray) = "UTF-8"
}
