struct DumbIterator<'a> {
    data: &'a [u8],
}

impl<'a> Iterator for DumbIterator<'a> {
    type Item = &'a [u8];

    fn next(&mut self) -> Option<S<caret>elf::Item> {
        None
    }
}
