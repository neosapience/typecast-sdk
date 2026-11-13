use std::time::Duration;
use typecast_rust::{ClientConfig, TypecastClient, TypecastError};

const PATHS: [&str; 5] = [
    "/v3/voices",
    "/v3/voices/tc_fixture",
    "/v1/custom-voices",
    "/v1/custom-voices/uc_fixture",
    "/v1/voices/recommendations",
];

async fn request(client: &TypecastClient, path: &str) -> Result<(), TypecastError> {
    match path {
        "/v3/voices" => client.get_voices_v3(None).await.map(|_| ()),
        "/v3/voices/tc_fixture" => client.get_voice_v3("tc_fixture").await.map(|_| ()),
        "/v1/custom-voices" => client.get_custom_voices().await.map(|_| ()),
        "/v1/custom-voices/uc_fixture" => client.get_custom_voice("uc_fixture").await.map(|_| ()),
        "/v1/voices/recommendations" => client.recommend_voices("fixture", None).await.map(|_| ()),
        _ => unreachable!("unknown test endpoint"),
    }
}

#[tokio::test]
async fn voice_endpoints_propagate_malformed_success_responses() {
    let mut server = mockito::Server::new_async().await;
    let client = TypecastClient::new(ClientConfig::new("").base_url(server.url())).unwrap();
    for path in PATHS {
        let response = server
            .mock("GET", path)
            .match_query(mockito::Matcher::Any)
            .with_status(200)
            .with_body("not valid JSON")
            .create_async()
            .await;
        match request(&client, path).await.unwrap_err() {
            TypecastError::HttpError(error) => assert!(error.is_decode(), "{path}: {error}"),
            error => panic!("{path}: unexpected error {error}"),
        }
        response.assert_async().await;
    }
}

#[tokio::test]
async fn voice_endpoints_propagate_transport_timeouts() {
    // Keep the port bound without replying, so no other process can capture it.
    let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let client = TypecastClient::new(
        ClientConfig::new("")
            .base_url(format!("http://{}", listener.local_addr().unwrap()))
            .timeout(Duration::from_millis(50)),
    )
    .unwrap();
    for path in PATHS {
        match request(&client, path).await.unwrap_err() {
            TypecastError::HttpError(error) => assert!(error.is_timeout(), "{path}: {error}"),
            error => panic!("{path}: unexpected error {error}"),
        }
    }
}
