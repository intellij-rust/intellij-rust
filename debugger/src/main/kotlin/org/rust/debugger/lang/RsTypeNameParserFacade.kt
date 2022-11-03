/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.jetbrains.annotations.TestOnly
import org.rust.debugger.lang.RsTypeNameParser.TypeNameContext

object RsTypeNameParserFacade {

    fun parse(typeName: String): TypeNameContext? = doParse(typeName) {
        it.parseTypeName().typeName()
    }

    @TestOnly
    fun parseToStringTree(typeName: String): String? = doParse(typeName) {
        it.parseTypeName().typeName().toStringTree(it)
    }

    private fun <T> doParse(typeName: String, block: (RsTypeNameParser) -> T?): T? {
        val stream = CharStreams.fromString(typeName)
        val lexer = RsTypeNameLexer(stream).apply {
            configureErrorListeners()
        }
        val tokens = CommonTokenStream(lexer)
        val parser = RsTypeNameParser(tokens).apply {
            configureErrorListeners()
        }

        return try {
            block(parser)
        } catch (e: RuntimeException) {
            when (e) {
                is ParseCancellationException, is RecognitionException -> return null
                else -> throw e
            }
        }
    }

    private fun Recognizer<*, *>.configureErrorListeners() {
        removeErrorListeners()
        addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                throw ParseCancellationException("($line:$charPositionInLine)")
            }
        })
    }
}
