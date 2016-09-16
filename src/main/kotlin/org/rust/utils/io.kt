package org.rust.utils

import java.io.DataInput
import java.io.DataOutput

fun <T> DataInput.readNullable(inner: DataInput.() -> T): T? = if (readBoolean()) null else inner()
fun <T> DataOutput.writeNullable(value: T?, inner: DataOutput.(T) -> Unit) =
    if (value == null) {
        writeBoolean(true)
    } else {
        writeBoolean(false)
        inner(value)
    }
