package org.rust.jps

import org.jdom.Element
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer

class RustJpsModulePropertiesSerializer :
           JpsModulePropertiesSerializer<JpsSimpleElement<*>>(RustJpsModuleType, "RUST_MODULE", null) {

    override fun loadProperties(componentElement: Element?): JpsSimpleElement<*> =
        JpsSimpleElementImpl<Any>(null)

    override fun saveProperties(properties: JpsSimpleElement<*>, componentElement: Element) {}
}
