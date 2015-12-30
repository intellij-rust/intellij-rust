package org.rust.jps;

import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.module.JpsModuleType;

public class JpsRustModuleType
        extends JpsElementTypeBase<JpsSimpleElement<?>>
        implements JpsModuleType<JpsSimpleElement<?>> {
    public static final JpsRustModuleType INSTANCE = new JpsRustModuleType();

}
