package org.rust.ide.utils

import com.intellij.openapi.util.io.StreamUtil

fun Any.loadCodeSampleResource(resource: String): String {
    val stream = javaClass.classLoader.getResourceAsStream(resource)
    // We need to convert line separators here, because IntelliJ always expects \n,
    // while on Windows the resource file will be read with \r\n as line separator.
    return StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
}
