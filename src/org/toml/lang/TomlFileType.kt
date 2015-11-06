package org.toml.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.toml.lang.icons.TomlIcons

public class TomlFileType : LanguageFileType(TomlLanguage.INSTANCE) {
    public object DEFAULTS {
        val EXTENSION = "toml";
        val DESCRIPTION = "TOML file";
    }

    override fun getName() = DEFAULTS.DESCRIPTION
    override fun getDescription() = DEFAULTS.DESCRIPTION
    override fun getDefaultExtension() = DEFAULTS.EXTENSION
    override fun getIcon() = TomlIcons.FILE
    override fun getCharset(file: VirtualFile, content: ByteArray) = "UTF-8"

    companion object {
        public val INSTANCE: TomlFileType = TomlFileType()
    }
}
