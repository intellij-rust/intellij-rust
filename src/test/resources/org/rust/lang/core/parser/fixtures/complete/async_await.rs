async fn foo() {}

fn main() {
    let async = ();
    let await = ();

    let _ = async;
    let _ = await;

    let _ = async!();
    let _ = await!(await);
    let _ = await!(foo());
    let _ = await!(async { () });

    async { () };
    async || { () };
    async move || { () };
}
