fn main() {
    let foo = moo.boo().goo()
                 .foo().bar()
                 .map(|x| x.foo().doo()
                           .moo().boo()
                           .bar().baz())
                 .baz().moo();
}
