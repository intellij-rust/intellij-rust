struct <info>T</info>(<info>i32</info>);

struct <info>S</info>{ <info>field</info>: <info>T</info>}

fn <info>main</info>() {
    let s = <info>S</info>{ <info>field</info>: <info>T</info>(92) };
    s.<info>field</info>.0;
}
