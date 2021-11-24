/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import org.rust.lang.core.macros.tt.FlatTree
import org.rust.lang.core.macros.tt.FlatTreeJsonDeserializer
import org.rust.stdext.RsResult
import org.rust.util.RsJacksonDeserializer
import java.io.IOException

sealed class Response {
    // data class ListMacros(...)
    data class ExpandMacro(val expansion: RsResult<FlatTree, PanicMessage>) : Response()
}

data class PanicMessage(val message: String)

class ResponseJsonDeserializer : RsJacksonDeserializer<Response>(Response::class.java) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Response {
        return context.readSingleFieldObject { key ->
            when (key) {
                "ExpandMacro" -> {
                    val r = context.readSingleFieldObject { key1 ->
                        when (key1) {
                            "Ok" -> RsResult.Ok(FlatTreeJsonDeserializer.deserialize(parser, context))
                            "Err" -> RsResult.Err(PanicMessage(readString()))
                            else -> context.reportInputMismatch("Unknown variant `$key1`, `Ok` or `Err` expected")
                        }
                    }
                    Response.ExpandMacro(r)
                }
                else -> context.reportInputMismatch("Unknown response kind `$key`")
            }
        }
    }
}
