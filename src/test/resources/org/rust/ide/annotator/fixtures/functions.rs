fn <info>main</info>() {}

struct <info>S</info>;

impl <info>S</info> {
    fn <info>foo</info>() {}
}

trait <info>T</info> {
    fn <info>foo</info>();
    fn <info>bar</info>() {}
}

impl <info>T</info> for <info>S</info> {
    fn <info>foo</info>() {}
}
