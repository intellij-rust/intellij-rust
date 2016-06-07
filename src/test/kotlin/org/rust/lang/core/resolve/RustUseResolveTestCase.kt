package org.rust.lang.core.resolve

class RustUseResolveTestCase : RustResolveTestCaseBase() {
    override fun getTestDataPath() = super.getTestDataPath() + "/use"

    fun testViewPath()                   = checkIsBound()
    fun testUsePath()                    = checkIsBound()
    fun testChildFromParent()            = checkIsBound(atOffset = 117)
    fun testPathRename()                 = checkIsBound(atOffset = 3)
    fun testDeepRedirection()            = checkIsBound(atOffset = 21)
    fun testRelativeChild()              = checkIsBound()
    fun testViewPathGlobIdent()          = checkIsBound()
    fun testViewPathGlobSelf()           = checkIsBound(atOffset = 42)
    fun testViewPathGlobSelfFn()         = checkIsBound(atOffset = 3)
    fun testUseGlobIdent()               = checkIsBound(atOffset = 21)
    fun testUseGlobSelf()                = checkIsBound(atOffset = 21)
    fun testUseGlobAlias()               = checkIsBound(atOffset = 21)
    fun testUseGlobRedirection()         = checkIsBound(atOffset = 21)
    fun testEnumVariant()                = checkIsBound()
    fun testSuperPart()                  = checkIsBound(atOffset = 0)
    fun testWildcard()                   = checkIsBound()
    fun testSuperWildcard()              = checkIsBound()
    fun testTwoWildcards()               = checkIsBound()
    fun testNestedWildcards()            = checkIsBound()
    fun testOnlyBraces()                 = checkIsBound()
    fun testColonBraces()                = checkIsBound()
    fun testLocalUse()                   = checkIsBound(atOffset = 25)
    fun testWildcardPriority()           = checkIsBound(atOffset = 52)

    fun testNoUse()                      = checkIsUnbound()
    fun testCycle()                      = checkIsUnbound()
    fun testUseGlobCycle()               = checkIsUnbound()
    fun testEmptyGlobList()              = checkIsUnbound()
    fun testWildcardCycle()              = checkIsUnbound()
}
