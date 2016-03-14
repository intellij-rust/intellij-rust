struct S;

impl S {
    fn foo(&self) -> &S {
        s<caret>elf
    }
}
