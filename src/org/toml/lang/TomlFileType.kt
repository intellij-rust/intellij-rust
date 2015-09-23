package org.toml.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.toml.lang.icons.TomlIcons
import javax.swing.Icon

/**
 * @author Aleksey.Kladov
 */
public class TomlFileType : LanguageFileType(TomlLanguage.INSTANCE) {
    object DEFAULTS {
        val EXTENSION: String = "toml"
    }

    private var myIcon: Icon = TomlIcons.NORMAL

    override fun getName(): String =
            "TOML file"

    override fun getDescription(): String =
            "TOML file"

    override fun getDefaultExtension(): String =
            DEFAULT_EXTENSION

    override fun getIcon(): Icon =
            myIcon

    override fun getCharset(file: VirtualFile, content: ByteArray): String =
            "UTF-8"

    companion object {
        public val INSTANCE: TomlFileType = TomlFileType()
        public val DEFAULT_EXTENSION: String = "toml"
    }

}
