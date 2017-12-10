/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.platform.DirectoryProjectGenerator

open class RsProjectSettingsStep(generator: DirectoryProjectGenerator<ConfigurationData>)
    : ProjectSettingsStepBase<ConfigurationData>(generator, AbstractNewProjectStep.AbstractCallback<ConfigurationData>())
