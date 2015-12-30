package org.rust.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;

import java.util.Collections;
import java.util.List;

public class JpsRustModelSerializerExtension extends JpsModelSerializerExtension {

    @NotNull
    @Override
    public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
        return Collections.singletonList(new JpsRustModulePropertiesSerializer());
    }
}
