/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import gnu.trove.TIntArrayList

abstract class RsJacksonSerializer<T>(t: Class<T>) : StdSerializer<T>(t) {
    protected inline fun JsonGenerator.writeJsonObject(writeBody: JsonGenerator.() -> Unit) {
        writeStartObject()
        writeBody()
        writeEndObject()
    }

    protected inline fun JsonGenerator.writeJsonObjectWithSingleField(name: String, writeValue: JsonGenerator.() -> Unit) {
        writeStartObject()
        writeFieldName(name)
        writeValue()
        writeEndObject()
    }

    protected inline fun JsonGenerator.writeField(name: String, writeValue: JsonGenerator.() -> Unit) {
        writeFieldName(name)
        writeValue()
    }

    protected inline fun <T : Any> JsonGenerator.writeNullableField(name: String, value: T?, writeValue: JsonGenerator.(T) -> Unit) {
        writeFieldName(name)
        if (value != null) {
            writeValue(value)
        } else {
            writeNull()
        }
    }

    protected inline fun <T> JsonGenerator.writeArrayField(name: String, list: Iterable<T>, writeElement: JsonGenerator.(T) -> Unit) {
        writeFieldName(name)
        writeArray(list, writeElement)
    }

    protected inline fun <T> JsonGenerator.writeArray(list: Iterable<T>, writeElement: JsonGenerator.(T) -> Unit) {
        writeStartArray()
        for (e in list) {
            writeElement(e)
        }
        writeEndArray()
    }

    protected fun JsonGenerator.writeArrayField(name: String, list: TIntArrayList, writeElement: JsonGenerator.(Int) -> Unit) {
        writeFieldName(name)
        writeArray(list, writeElement)
    }

    protected fun JsonGenerator.writeArray(list: TIntArrayList, writeElement: JsonGenerator.(Int) -> Unit) {
        writeStartArray()
        list.forEach {
            writeElement(it)
            true
        }
        writeEndArray()
    }
}
