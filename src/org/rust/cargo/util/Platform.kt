package org.rust.cargo.util

object Platform {

    fun getCanonicalNativeExecutableName(fileName: String): String {
        return when (detectOperatingSystem()) {
            OS.Windows  -> fileName + ".exe"
            else        -> fileName
        }
    }

    enum class OS {
        Windows,
        Mac,
        Linux,
        Other
    }

    private val OS_NAME_PROPERTY_PATH = "os.name"

    private fun detectOperatingSystem(): OS {
        val name = System.getProperty(OS_NAME_PROPERTY_PATH).toLowerCase()

        return when {
            name.contains("windows") -> OS.Windows
            name.contains("mac")     -> OS.Mac
            name.contains("linux")   -> OS.Linux
            else                     -> OS.Other
        }
    }

}
