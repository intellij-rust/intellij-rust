// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print a
// gdbg-check:[...]$1 = enum::EnumA::Var3 = {Var3 = enum::EnumA::Var3 = {a = 5, b = enum::TestEnumB::Var2 = {Var2 = enum::TestEnumB::Var2 = {a = 5, b = "hello", c = enum::EnumC::Var1 = {Var1 = size=1 = {8}}}}}}

// gdb-command:print d
// gdbg-check:[...]$2 = enum::EnumD

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

enum EnumD {}

fn main() {
    let a = EnumA::Var3 {
        a: 5,
        b: TestEnumB::Var2 {
            a: 5,
            b: "hello".to_owned(),
            c: EnumC::Var1(8),
        },
    };
    let d: EnumD;
    print!(""); // #break
}
