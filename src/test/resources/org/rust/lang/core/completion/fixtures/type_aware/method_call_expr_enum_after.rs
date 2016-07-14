enum E { X }

impl E {
    fn quux(&self) {}

    fn bar(&self) {
        self.quux
    }
}
