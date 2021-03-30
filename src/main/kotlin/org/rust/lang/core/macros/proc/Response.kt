/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.TokenTreeJsonDeserializer
import org.rust.util.RsJacksonDeserializer
import java.io.IOException

sealed class Response {
    data class Error(val message: String) : Response()
    data class ExpansionMacro(val expansion: TokenTree.Subtree) : Response()
}

class ResponseJsonDeserializer : RsJacksonDeserializer<Response>(Response::class.java) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Response {
        return context.readSingleFieldObject { key ->
            when (key) {
                "Error" -> context.readValue<Response.Error>()
                "ExpansionMacro" -> {
                    val r = context.readSingleFieldObject {
                        if (it != "expansion") context.reportInputMismatch("Unknown field `$it`, `expansion` expected")
                        TokenTreeJsonDeserializer.readSubtree(context)
                    }
                    Response.ExpansionMacro(r)
                }
                else -> context.reportInputMismatch("Unknown response kind `$key`")
            }
        }
    }
}
