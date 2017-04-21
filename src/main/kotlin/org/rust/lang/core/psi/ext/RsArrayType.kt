package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType

val RsArrayType.arraySize: Int? get() {
    val stub = stub
    if (stub != null) {
        return if (stub.arraySize == -1) null else stub.arraySize
    }

    return try {
        expr?.text?.toInt() // TODO need more precise handling
    } catch (e: NumberFormatException) {
        return null
    }
}
