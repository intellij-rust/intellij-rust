fn foo() {
    write!(&mut self.destination, "{}",
           magic_number);
    write![&mut self.destination, "{}",
           magic_number];
    write! {&mut self.destination, "{}",
            magic_number};
}
