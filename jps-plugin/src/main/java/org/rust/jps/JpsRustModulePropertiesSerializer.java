package org.rust.jps;

import org.jdom.Element;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;

public class JpsRustModulePropertiesSerializer extends JpsModulePropertiesSerializer<JpsSimpleElement<?>> {

    public JpsRustModulePropertiesSerializer() {
        super(JpsRustModuleType.INSTANCE, "RUST_EXECUTABLE_MODULE", null);
    }

    @Override
    public JpsSimpleElement<?> loadProperties(Element componentElement) {
        return new JpsSimpleElementImpl<Object>(null);
    }

    @Override
    public void saveProperties(JpsSimpleElement<?> properties, Element componentElement) {

    }
}
