package org.rust.cargo.util

import com.intellij.openapi.util.SystemInfo

object PlatformUtil {

    /**
     * Adjusts filename to become canonical executable one (adding 'exe' extension on Windows, for example)
     */
    fun getCanonicalNativeExecutableName(fileName: String): String {
        return if (SystemInfo.isWindows) "$fileName.exe" else fileName
    }

}
