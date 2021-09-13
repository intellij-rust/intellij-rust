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
    let _ = await.await;
    let _ = foo().await;
    let _ = async { () }.await;

    async { () };
    async || { () };
    async move || { () };
    static move || { () };

    async fn nested() {}
}
