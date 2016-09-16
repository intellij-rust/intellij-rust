package org.rust.utils

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

interface RustDataExternalizer<T> : DataExternalizer<T> {
    override fun save(output: DataOutput, value: T)
    override fun read(input: DataInput): T

    fun nullable(): RustDataExternalizer<T?> = object : RustDataExternalizer<T?> {
        override fun save(output: DataOutput, value: T?) {
            if (value == null) {
                output.writeBoolean(true)
            } else {
                output.writeBoolean(false)
                this@RustDataExternalizer.save(output, value)
            }
        }

        override fun read(input: DataInput): T? {
            return if (input.readBoolean()) {
                null
            } else {
                this@RustDataExternalizer.read(input)
            }
        }
    }
}
