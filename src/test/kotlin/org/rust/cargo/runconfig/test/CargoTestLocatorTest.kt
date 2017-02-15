package org.rust.cargo.runconfig.test

import com.intellij.psi.search.GlobalSearchScope
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFunction

class CargoTestLocatorTest : RsTestBase() {
    override val dataPath: String = ""

    fun testSimpleSelfReferences() = doSelfReferencesTest(
        """
        #[test]
        fn foo() {}
          //^
        """
    )

    private fun doSelfReferencesTest(@Language("Rust") source: String) {
        InlineFile(source)
        val func = findElementInEditor<RsFunction>()
        val url = CargoTestLocator.getUrl(func)
        val locations = CargoTestLocator.getLocation(url, project, GlobalSearchScope.allScope(project))
        assert(locations.isNotEmpty()) { "generated url doesn't resolve to anything" }
        assert(locations.size == 1) { "generated url resolves to multiple things" }
        assert(locations.single().psiElement == func) { "location didn't resolve to given psi element" }
    }
}
