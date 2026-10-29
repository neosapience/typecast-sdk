use typecast_rust::{ClientConfig, TTSModel, TypecastClient, VoicesV2Filter};

#[test]
fn v31_round_trip_keeps_the_existing_default() {
    assert_eq!(
        serde_json::to_string(&TTSModel::SsfmV31).unwrap(),
        "\"ssfm-v31\""
    );
    assert_eq!(
        serde_json::from_str::<TTSModel>("\"ssfm-v31\"").unwrap(),
        TTSModel::SsfmV31
    );
    assert_eq!(TTSModel::default(), TTSModel::SsfmV30);
}

#[tokio::test]
async fn v31_voice_filter_reaches_the_v3_endpoint() {
    let mut server = mockito::Server::new_async().await;
    let response = server
        .mock("GET", "/v3/voices")
        .match_query(mockito::Matcher::UrlEncoded(
            "model".into(),
            "ssfm-v31".into(),
        ))
        .with_status(200)
        .with_body("[]")
        .create_async()
        .await;
    let client = TypecastClient::new(ClientConfig::new("").base_url(server.url())).unwrap();
    let voices = client
        .get_voices_v3(Some(VoicesV2Filter::new().model(TTSModel::SsfmV31)))
        .await
        .unwrap();
    assert!(voices.is_empty());
    response.assert_async().await;
}
