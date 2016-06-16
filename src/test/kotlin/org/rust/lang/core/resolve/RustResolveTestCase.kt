package org.rust.lang.core.resolve

class RustResolveTestCase : RustResolveTestCaseBase() {

    fun testFunctionArgument() = checkIsBound()
    fun testLocals() = checkIsBound(atOffset = 19)
    fun testShadowing() = checkIsBound(atOffset = 35)
    fun testNestedPatterns() = checkIsBound()
    fun testClosure() = checkIsBound()
    fun testMatch() = checkIsBound()
    fun testIfLet() = checkIsBound()
    fun testIfLetX() = checkIsUnbound()
    fun testWhileLet() = checkIsBound()
    fun testWhileLetX() = checkIsUnbound()
    fun testFor() = checkIsBound()
    fun testTraitMethodArgument() = checkIsBound()
    fun testImplMethodArgument() = checkIsBound()
    fun testStructPatterns1() = checkIsBound(atOffset = 69)
    fun testStructPatterns2() = checkIsBound()

    fun testModItems1() = checkIsBound()
    fun testModItems2() = checkIsBound()
    fun testModItems3() = checkIsUnbound()
    fun testModItems4() = checkIsBound()
    fun testModItems5() = checkIsUnbound()

    fun testCrateItems() = checkIsBound()
    fun testNestedModule() = checkIsBound(atOffset = 55)
    fun testSelf() = checkIsBound()
    fun testSelfIdentifier() = checkIsBound(atOffset = 32)
    fun testSuper() = checkIsBound()
    fun testFormatPositional() = checkIsBound()
    fun testFormatNamed() = checkIsBound()

    fun testEnumVariant1() = checkIsBound()
    fun testEnumVariant2() = checkIsUnbound()

    fun testNestedSuper() = checkIsBound()
    fun testLocalFn() = checkIsBound()
    fun testStructField() = checkIsBound()
    fun testEnumField() = checkIsBound()
    fun testNonGlobalPathWithColons() = checkIsBound()
    fun testTypeAlias() = checkIsBound()
    fun testTrait() = checkIsBound()
    fun testTypeAliasGenerics() = checkIsBound()
    fun testForeignFn() = checkIsBound()
    fun testForeignStatic() = checkIsBound()

    fun testSelfType() = checkIsUnbound() // TODO: some form of resolve for Self should be implemented

    fun testLetCycle1() = checkIsUnbound()
    fun testLetCycle2() = checkIsBound(atOffset = 20)
    fun testLetCycle3() = checkIsUnbound()

    fun testUnbound() = checkIsUnbound()
    fun testOrdering() = checkIsUnbound()
    fun testModBoundary() = checkIsUnbound()
    fun testFollowPath() = checkIsUnbound()
    fun testWrongSelf() = checkIsUnbound()
    fun testSelfInStatic() = checkIsUnbound()
    fun testWrongSuper() = checkIsUnbound()

    fun testFunctionIsNotModule() = checkIsUnbound()

    fun testEnumGenerics() = checkIsUnbound()
}

