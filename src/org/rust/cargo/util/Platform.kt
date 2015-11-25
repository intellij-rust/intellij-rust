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

        if (name.contains("windows"))
            return OS.Windows
        else if (name.contains("mac"))
            return OS.Mac
        else if (name.contains("linux"))
            return OS.Linux
        else
            return OS.Other
    }

}
