package org.rust.jps

import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer

class RustJpsModelSerializerExtension : JpsModelSerializerExtension() {

    override fun getModulePropertiesSerializers(): List<JpsModulePropertiesSerializer<*>> =
        listOf(RustJpsModulePropertiesSerializer())
}
