fn main() {
    'label: while let Some(_) = Some(92) {}

    let _  = loop { break 92 };
    let _ = 'l: loop { break 'l 92 };

    'll: loop {
        break 'll;
    }
}
