package org.rust.jps

import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.ex.JpsElementTypeBase
import org.jetbrains.jps.model.module.JpsModuleType

object RustJpsModuleType : JpsElementTypeBase<JpsSimpleElement<*>>(), JpsModuleType<JpsSimpleElement<*>>
