package org.rust.lang.navigation.goto

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.index.RustSymbolIndex

class RustSymbolContributor : ChooseByNameContributor {
    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<out String> {
        if (project == null) {
            return emptyArray()
        }
        return RustSymbolIndex.getNames(project).toTypedArray()
    }

    override fun getItemsByName(name: String?, pattern: String?, project: Project?, includeNonProjectItems: Boolean): Array<out NavigationItem> {
        if (project == null || name == null) {
            return emptyArray()
        }
        val scope = if (includeNonProjectItems) {
            GlobalSearchScope.allScope(project)
        } else {
            GlobalSearchScope.projectScope(project)
        }
        return RustSymbolIndex.getItemsByName(name, project, scope).toTypedArray()
    }

}
