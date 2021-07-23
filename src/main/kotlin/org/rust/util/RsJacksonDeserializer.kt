/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.util

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

abstract class RsJacksonDeserializer<T>(vc: Class<T>) : StdDeserializer<T>(vc) {
    protected inline fun <T> DeserializationContext.readSingleFieldObject(parseValue: DeserializationContext.(key: String) -> T): T {
        expectToken(JsonToken.START_OBJECT)
        val propertyName = expectNextFieldName()
        parser.nextToken()
        val result = parseValue(propertyName)
        expectNextToken(JsonToken.END_OBJECT)
        return result
    }

    protected inline fun DeserializationContext.readObjectFields(parseValue: DeserializationContext.(key: String) -> Unit) {
        expectToken(JsonToken.START_OBJECT)
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            expectToken(JsonToken.FIELD_NAME)
            val propertyName = parser.currentName
            if (parser.nextToken() != JsonToken.VALUE_NULL) {
                parseValue(propertyName)
            }
        }
    }

    protected inline fun <T> DeserializationContext.readArray(out: MutableList<T>, parseValue: DeserializationContext.() -> T) {
        expectToken(JsonToken.START_ARRAY)
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            out += parseValue()
        }
    }

    protected inline fun <reified T> DeserializationContext.readValue(): T =
        readValue(parser, T::class.java)

    protected fun DeserializationContext.readString(): String = _parseString(parser, this)
    protected fun DeserializationContext.readLong(): Long = _parseLongPrimitive(parser, this)

    protected fun DeserializationContext.expectToken(expectedToken: JsonToken) {
        if (parser.currentToken != expectedToken) wrongToken(expectedToken)
    }

    protected fun DeserializationContext.expectNextToken(expectedToken: JsonToken) {
        if (parser.nextToken() != expectedToken) wrongToken(expectedToken)
    }

    protected fun DeserializationContext.expectNextFieldName(): String {
        return parser.nextFieldName() ?: wrongToken(JsonToken.FIELD_NAME)
    }

    private fun DeserializationContext.wrongToken(expectedToken: JsonToken): Nothing {
        reportWrongTokenException(handledType(), expectedToken, null)
        error("unreachable: `reportWrongTokenException` always throws an exception")
    }

    protected fun DeserializationContext.reportInputMismatch(msg: String, vararg args: Any): Nothing {
        return reportInputMismatch(handledType(), msg, *args)
    }
}
