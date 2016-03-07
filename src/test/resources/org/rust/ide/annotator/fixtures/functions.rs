fn <info>main</info>() {}

struct S;

impl S {
    fn <info>foo</info>() {}
}

trait T {
    fn <info>foo</info>();
    fn <info>bar</info>() {}
}

impl T for S {
    fn <info>foo</info>() {}
}
