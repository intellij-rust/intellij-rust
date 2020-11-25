
from typing import Any

class SBType:
    ...

class SBValue:
    def IsValid(self) -> bool: ...
    def MightHaveChildren(self) -> bool: ...
    def GetNumChildren(self) -> int: ...
    def GetChildAtIndex(self, index: int) -> SBValue: ...
    def GetIndexOfChildWithName(self, name: str) -> int: ...
    def SetPreferSyntheticValue(self, on: bool): ...
    def SetFormat(self, fmt: int): ...

class SBError:
    def Success(self) -> bool: ...
    def GetCString(self) -> str: ...
    def SetErrorString(self, s: str): ...

class SBData:
    def SetData(self, err: SBError, buffer: Any, size: int, )

class SBTypeSynthetic:
    @staticmethod
    def CreateWithClassName(cls_name: str): ...

class SBTypeSummary:
    @staticmethod
    def CreateWithFunctionName(fn_name: str): ...

class SBTypeNameSpecifier:
    def __init__(self, name: str, is_regex: bool): ...

class SBExecutionContext:
    ...

class SBModule:
    ...

class SBTarget:
    def GetAddressByteSize(self) -> int: ...

eTypeOptionCascade: int

eBasicTypeInvalid: int
eBasicTypeVoid: int
eBasicTypeChar: int
eBasicTypeSignedChar: int
eBasicTypeUnsignedChar: int
eBasicTypeWChar: int
eBasicTypeSignedWChar: int
eBasicTypeUnsignedWChar: int
eBasicTypeChar16: int
eBasicTypeChar32: int
eBasicTypeShort: int
eBasicTypeUnsignedShort: int
eBasicTypeInt: int
eBasicTypeUnsignedInt: int
eBasicTypeLong: int
eBasicTypeUnsignedLong: int
eBasicTypeLongLong: int
eBasicTypeUnsignedLongLong: int
eBasicTypeInt128: int
eBasicTypeUnsignedInt128: int
eBasicTypeBool: int
eBasicTypeHalf: int
eBasicTypeFloat: int
eBasicTypeDouble: int
eBasicTypeLongDouble: int
eBasicTypeFloatComplex: int
eBasicTypeDoubleComplex: int
eBasicTypeLongDoubleComplex: int
eBasicTypeObjCID: int
eBasicTypeObjCClass: int
eBasicTypeObjCSel: int
eBasicTypeNullPtr: int

eFormatChar: int

eTypeClassStruct: int
