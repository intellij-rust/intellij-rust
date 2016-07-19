trait <info>MyTrait</info> {
    type AssocType;
    fn <info>some_fn</info>(&self);
}

struct <info>MyStruct</info><<info>N</info>: ?<info>Sized</info>+<info>Debug</info>+<info><info>MyTrait</info></info>> {
    <info>N</info>: my_field
}
