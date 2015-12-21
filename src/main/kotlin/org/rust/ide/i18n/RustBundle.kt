package org.rust.ide.i18n

import com.intellij.CommonBundle
import org.jetbrains.annotations.PropertyKey
import java.util.*

object RustBundle {
    private const val BUNDLE_NAME = "org.rust.ide.i18n.RustBundle"

    public val BUNDLE by lazy {
        ResourceBundle.getBundle(BUNDLE_NAME)
    }

    public fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
        return CommonBundle.message(BUNDLE, key, params)
    }
}
