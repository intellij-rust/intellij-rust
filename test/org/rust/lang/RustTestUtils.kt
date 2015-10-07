package org.rust.lang

import com.intellij.openapi.application.PathManager
import kotlin.platform.platformStatic

public object RustTestUtils {

    @platformStatic
    public fun setTestDataPath() {
        System.setProperty(PathManager.PROPERTY_HOME_PATH, "testData")
    }

}