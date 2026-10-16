import SwiftUI
import Observation

enum AuthPhase: Equatable {
    case launching
    case signedOut
    case signedIn
}

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let title: String
    var message: String? = nil
    var isError = false
}

@MainActor
@Observable
final class AppState {
    var phase: AuthPhase = .launching
    var user: AppUser?
    var toast: Toast?
    var confettiTrigger = 0
    /// Bumped whenever collection / wishlist data changes so other tabs reload.
    var dataVersion = 0
    /// Deep link to a public profile (mangavault://u/<id>).
    var pendingProfileId: String?
    /// Programmatic navigation request from anywhere (e.g. a tapped notification).
    var openRoute: Route?
    /// Programmatic tab switch (e.g. dashboard "My Collection" button).
    var requestedTab: MainTab?

    func selectTab(_ tab: MainTab) { requestedTab = tab }

    // Social counters (refreshed by polling).
    var unreadMessages = 0
    var pendingFriendRequests = 0
    var unreadNotifications = 0
    var notifications: [VaultNotification] = []

    private var toastTask: Task<Void, Never>?

    init() {
        Base44Client.shared.onUnauthorized = { [weak self] in
            Task { @MainActor in self?.signOut() }
        }
    }

    var displayName: String {
        if let n = user?.full_name, !n.isEmpty { return n }
        if let e = user?.email { return String(e.split(separator: "@").first ?? "") }
        return "Reader"
    }

    func bootstrap() async {
        guard Base44Client.shared.token != nil else { phase = .signedOut; return }
        do {
            user = try await Base44Client.shared.me()
            phase = .signedIn
        } catch let e as APIError where e.status == 401 || e.status == 403 {
            signOut()
        } catch {
            // Offline or server hiccup: keep the session, screens will show their own errors.
            phase = .signedIn
        }
    }

    func didSignIn() async {
        await ProfileStore.shared.invalidate()
        user = try? await Base44Client.shared.me()
        phase = .signedIn
    }

    func signOut() {
        Base44Client.shared.logout()
        user = nil
        unreadMessages = 0; pendingFriendRequests = 0; unreadNotifications = 0; notifications = []
        Task { await ProfileStore.shared.invalidate() }
        phase = .signedOut
    }

    func show(_ title: String, _ message: String? = nil, error: Bool = false) {
        toast = Toast(title: title, message: message, isError: error)
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.6))
            if !Task.isCancelled { self.toast = nil }
        }
    }

    func fail(_ title: String, _ error: Error) {
        show(title, error.localizedDescription, error: true)
    }

    func dataChanged() { dataVersion += 1 }

    func refreshSocialCounts() async {
        guard phase == .signedIn else { return }
        if let n = try? await Social.notifications() { notifications = n.items; unreadNotifications = n.unread }
        if let c = try? await Social.conversations() { unreadMessages = c.reduce(0) { $0 + ($1.unread ?? 0) } }
        if let f = try? await Social.overview() { pendingFriendRequests = f.incoming.count }
    }

    func celebrate() { confettiTrigger += 1 }

    func handle(url: URL) {
        guard url.scheme == Config.urlScheme else { return }
        // mangavault://u/<id>
        if url.host == "u", let id = url.pathComponents.dropFirst().first { pendingProfileId = id }
    }
}
