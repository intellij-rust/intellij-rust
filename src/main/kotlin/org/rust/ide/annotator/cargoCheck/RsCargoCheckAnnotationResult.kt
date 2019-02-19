/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.google.gson.JsonParser
import org.rust.cargo.toolchain.CargoTopMessage

class RsCargoCheckAnnotationResult(commandOutput: List<String>) {
    companion object {
        private val parser = JsonParser()
        private val messageRegex = """\s*\{.*"message".*""".toRegex()
    }

    val messages: List<CargoTopMessage> = commandOutput.asSequence()
        .filter { messageRegex.matches(it) }
        .map { parser.parse(it) }
        .filter { it.isJsonObject }
        .mapNotNull { CargoTopMessage.fromJson(it.asJsonObject) }
        .toList()
}
