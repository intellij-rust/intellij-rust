package org.rust.wsl

import com.intellij.wsl.WSLCredentialsContribution
import com.intellij.wsl.WSLCredentialsHolder
import org.rust.remote.RsCredentialsContribution

class RsWslCredentialsContribution : WSLCredentialsContribution<RsCredentialsContribution<*>>(),
                                     RsCredentialsContribution<WSLCredentialsHolder> {
    override val isPackageManagementEnabled: Boolean = true
    override val isSpecificCoverageAttach: Boolean = false
    override val isSpecificCoveragePatch: Boolean = false
    override val isRemoteProcessStartSupported: Boolean = false
    override fun getLanguageContributionClass(): Class<RsCredentialsContribution<*>> = RsCredentialsContribution::class.java
    override fun getLanguageContribution(): RsWslCredentialsContribution = this
    override fun isValid(credentials: WSLCredentialsHolder?): Boolean = credentials != null
}
