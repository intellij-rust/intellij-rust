/// A cheap, reference-to-reference conversion.
///
/// `AsRef` is very similar to, but different than, `Borrow`. See
/// [the book][book] for more.
///
/// [book]: ../../book/borrow-and-asref.html
///
/// **Note: this trait must not fail**. If the conversion can fail, use a dedicated method which
/// returns an `Option<T>` or a `Result<T, E>`.
///
/// # Examples
///
/// Both `String` and `&str` implement `AsRef<str>`:
///
/// ```
/// fn is_hello<T: AsRef<str>>(s: T) {
///    assert_eq!("hello", s.as_ref());
/// }
///
/// let s = "hello";
/// is_hello(s);
///
/// let s = "hello".to_string();
/// is_hello(s);
/// ```
///
/// # Generic Impls
///
/// - `AsRef` auto-dereference if the inner type is a reference or a mutable
/// reference (eg: `foo.as_ref()` will work the same if `foo` has type `&mut Foo` or `&&mut Foo`)
///
#[stable(feature = "rust1", since = "1.0.0")]
pub trait AsRef<T: ?Sized> {
    /// Performs the conversion.
    #[stable(feature = "rust1", since = "1.0.0")]
    fn as_ref(&self) -> &T;
}

impl <T> AsR<caret>ef<T> {}
