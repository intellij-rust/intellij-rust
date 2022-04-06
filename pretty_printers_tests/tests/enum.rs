// min-version: 1.30.0

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print a
// lldbr-check:[...]
// lldbg-check:$0 = Var3 { a = 5 b = Var2 { a = 5 b = "hello" [...] } c = { Var1 = { 0 = 8 } } } }
// lldb-command:print direct_tag_enum
// lldbr-check:[...]
// lldbg-check:$1 = Tag1 { __0 = 42 }
// lldb-command:print niche_layout_enum
// lldbr-check:[...]
// lldbg-check:[...]$2 = Data { my_data = A }
// lldb-command:print option_some
// lldbr-check:[...]
// lldbg-check:[...]$3 = Some { __0 = 42 }
// lldb-command:print option_none
// lldbr-check:[...]
// lldbg-check:[...]$4 = None

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print a
// gdb-check:[...]$1 = enum::EnumA::Var3 = {Var3 = enum::EnumA::Var3 = {a = 5, b = enum::TestEnumB::Var2 = {Var2 = enum::TestEnumB::Var2 = {a = 5, b = "hello", c = enum::EnumC[...] = {Var1 = size=1 = {8}}}}}}


enum EnumA {
    Var1 { a: u32 },
    Var2(u64),
    Var3 { a: u32, b: TestEnumB },
}

enum TestEnumB {
    Var1(u64),
    Var2 { a: u32, b: String, c: EnumC },
}

enum EnumC {
    Var1(u64),
}

enum CStyleEnum { A, B }

enum DirectTag {
    Tag1(i32),
    Tag2(i32),
}

enum SingleVariantEnum {
    SingleVariant
}

enum NicheLayoutEnum {
    Tag1,
    Data { my_data: CStyleEnum },
    Tag2,
}

fn main() {
    let a = EnumA::Var3 {
        a: 5,
        b: TestEnumB::Var2 {
            a: 5,
            b: "hello".to_owned(),
            c: EnumC::Var1(8),
        },
    };
    let direct_tag_enum = DirectTag::Tag1(42);
    let niche_layout_enum = NicheLayoutEnum::Data { my_data: CStyleEnum::A };
    let option_some = Some(42);
    let option_none: Option<i32> = None;
    print!(""); // #break
}
