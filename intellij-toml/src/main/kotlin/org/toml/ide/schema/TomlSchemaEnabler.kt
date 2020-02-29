/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import org.toml.lang.psi.TomlFileType

class TomlSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile): Boolean = file.fileType == TomlFileType || file.name == "example.json"
}
