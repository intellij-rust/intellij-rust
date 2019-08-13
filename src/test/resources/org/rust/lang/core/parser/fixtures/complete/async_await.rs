async fn foo() {}
async fn bar() {}

trait T {
    async fn foo();
    async fn bar();
}

fn main() {
    let async = ();
    let await = ();

    let _ = async;
    let _ = await;

    let _ = async!();
    let _ = await!();
    let _ = await!(await);
    let _ = await!(foo());
    let _ = await!(async { () });

    async { () };
    async || { () };
    async move || { () };
    static move || { () };

    async fn nested() {}
}
