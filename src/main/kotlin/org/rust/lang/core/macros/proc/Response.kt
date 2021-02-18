/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import org.rust.lang.core.macros.tt.TokenTree
import java.lang.reflect.Type

sealed class Response {
    data class Error(val message: String) : Response()
    data class ExpansionMacro(val expansion: TokenTree.Subtree) : Response()
}

class ResponseJsonAdapter : JsonDeserializer<Response> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): Response? {
        val obj = json.asJsonObject
        return when {
            obj.has("Error") -> context.deserialize(obj["Error"], Response.Error::class.java)
            obj.has("ExpansionMacro") -> context.deserialize(obj["ExpansionMacro"], Response.ExpansionMacro::class.java)
            else -> null
        }
    }
}
