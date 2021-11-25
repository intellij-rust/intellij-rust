def FooSummaryProvider(valobj, _dict):
    # type: (SBValue, dict) -> str
    x = valobj.GetChildMemberWithName("x").GetValueAsSigned()
    y = valobj.GetChildMemberWithName("y").GetValueAsSigned()
    return "sum = {}".format(x + y)
