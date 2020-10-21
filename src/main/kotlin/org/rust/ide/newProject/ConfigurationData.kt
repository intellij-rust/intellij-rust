/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.projectRoots.Sdk

data class ConfigurationData(
    val sdk: Sdk?,
    val template: RsProjectTemplate
)
