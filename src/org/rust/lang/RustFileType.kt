package org.rust.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.rust.lang.icons.RustIcons
import javax.swing.Icon

public open class RustFileType : LanguageFileType(RustLanguage.INSTANCE) {

    public object INSTANCE : RustFileType() {}

    public object DEFAULTS {
        public val EXTENSION: String = "rs";
    }

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon = RustIcons.NORMAL;

    override fun getDefaultExtension(): String = DEFAULTS.EXTENSION

    override fun getDescription(): String {
        return "Rust Files"
    }

}

