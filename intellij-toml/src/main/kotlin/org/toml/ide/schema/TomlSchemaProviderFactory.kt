/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.toml.lang.psi.TomlFileType

class TomlSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(TomlSchemaFileProvider())
    }
}

class TomlSchemaFileProvider : JsonSchemaFileProvider {
    override fun getName(): String = "Example Toml Schema"

    override fun isAvailable(file: VirtualFile): Boolean = file.fileType == TomlFileType || file.name == "example.json"

    override fun getSchemaFile(): VirtualFile? {
        return JsonSchemaProviderFactory.getResourceFile(TomlSchemaFileProvider::class.java, "/schemas/example.json")
    }

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7
}
