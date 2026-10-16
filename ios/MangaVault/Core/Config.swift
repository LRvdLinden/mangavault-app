import Foundation

/// Backend configuration — the native app talks to the same Base44 backend
/// as https://mangas.base44.app, so accounts, collections and progress are shared.
enum Config {
    static let appBaseURL = URL(string: "https://mangas.base44.app")!
    static let apiBaseURL = URL(string: "https://mangas.base44.app/api")!
    static let appId = "6acd06d4ea8aff769f949a17"

    /// Custom URL scheme used for the Google sign-in callback and deep links
    /// (e.g. mangavault://u/<profileId>).
    static let urlScheme = "mangavault"

    static func publicProfileURL(_ id: String) -> URL {
        appBaseURL.appendingPathComponent("u").appendingPathComponent(id)
    }
}
