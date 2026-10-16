import SwiftUI

struct WishlistView: View {
    @Environment(AppState.self) private var app
    @State private var items: [WishlistItem] = []
    @State private var loading = true
    @State private var moving: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(lead: "MY", accent: "WISHLIST", accentColor: Theme.red, subtitle: "Manga volumes you want to buy next.")
                HStack(spacing: 10) {
                    StatCard(icon: "heart", label: "Items", value: "\(items.count)", accent: Theme.red)
                    StatCard(icon: "eurosign.circle", label: "Total Value", value: totalValue.euro, accent: Theme.red)
                }
                if loading && items.isEmpty {
                    LoadingView()
                } else if items.isEmpty {
                    EmptyState(icon: "heart", text: "Your wishlist is empty.\nTap the heart on any volume in the Catalog to save it here.")
                } else {
                    LazyVGrid(columns: gridColumns, spacing: 22) {
                        ForEach(items) { card($0) }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Wishlist")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task(id: app.dataVersion) { await load() }
    }

    private func badgeText(_ w: WishlistItem) -> String {
        if w.isOmnibus { return "3-IN-1" }
        return "VOL. \(w.volume ?? 0)"
    }

    private func badgeIcon(_ w: WishlistItem) -> String? {
        w.isOmnibus ? "books.vertical" : nil
    }

    private var totalValue: Double {
        items.reduce(0.0) { (sum: Double, i: WishlistItem) -> Double in sum + (i.price ?? 0) }
    }

    private func card(_ w: WishlistItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            NavigationLink(value: route(for: w)) {
                CoverView(info: CoverInfo(wish: w))
                    .overlay(alignment: .topTrailing) {
                        Badge(text: badgeText(w), icon: badgeIcon(w),
                              fg: Theme.red, bg: Theme.background.opacity(0.85), ring: Theme.red.opacity(0.5)).padding(8)
                    }
                    .overlay(alignment: .topLeading) {
                        Badge(text: "WISH", icon: "heart.fill", bg: Theme.red).padding(8)
                    }
            }
            .buttonStyle(.plain)
            Text(w.title ?? "").font(.subheadline.weight(.bold)).lineLimit(1)
            HStack {
                Text(w.volumeLabel).font(.caption).foregroundStyle(Theme.muted)
                Spacer()
                Text((w.price ?? 0).euro).font(.caption.weight(.bold)).foregroundStyle(Theme.red)
            }
            HStack(spacing: 6) {
                Button { Task { await bought(w) } } label: {
                    HStack(spacing: 4) {
                        if moving == w.id { ProgressView().tint(.white).controlSize(.mini) } else { Image(systemName: "bag.fill") }
                        Text("Bought")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle(color: Theme.red))
                .disabled(moving != nil)
                Button { Task { await remove(w) } } label: { Image(systemName: "trash") }
                    .buttonStyle(OutlineButtonStyle(color: Theme.muted))
            }
        }
        .contextMenu {
            Button { Task { await bought(w) } } label: { Label("Move to collection", systemImage: "bag") }
            Button(role: .destructive) { Task { await remove(w) } } label: { Label("Remove", systemImage: "trash") }
        }
    }

    private static func collectionPayload(from w: WishlistItem) -> [String: Any] {
        let title: String = w.title ?? ""
        var d: [String: Any] = [:]
        d["manga_id"] = (w.manga_id ?? "") as String
        d["title"] = title
        if let v = w.volume { d["volume_number"] = v } else { d["volume_number"] = NSNull() }
        d["volume_cover_url"] = (w.volume_cover_url ?? "") as String
        d["cover_image_url"] = (w.cover_image_url ?? "") as String
        d["cover_seed"] = (w.cover_seed ?? title) as String
        d["publisher"] = (w.publisher ?? "") as String
        let price: Double = w.price ?? 0
        d["price"] = price
        let sj: Bool = w.is_shonen_jump ?? false
        d["is_shonen_jump"] = sj
        d["edition"] = (w.edition ?? "standard") as String
        d["volumes_included"] = (w.volumes_included ?? "") as String
        d["omnibus_of"] = (w.omnibus_of ?? "") as String
        d["read"] = false
        return d
    }

    private func route(for w: WishlistItem) -> Route {
        if !w.isOmnibus, let v = w.volume { return .volume(mangaId: w.manga_id ?? "", volume: v) }
        return .manga(w.manga_id ?? "")
    }

    private func load() async {
        loading = true
        do { items = try await VaultService.wishlist() }
        catch { if !(error is CancellationError) { app.fail("Could not load wishlist", error) } }
        loading = false
    }

    /// "Bought" — creates the collection item and removes the wish (same as the web app).
    private func bought(_ w: WishlistItem) async {
        moving = w.id; defer { moving = nil }
        do {
            let payload: [String: Any] = Self.collectionPayload(from: w)
            _ = try await Base44Client.shared.create("CollectionItem", payload, CollectionItem.self)
            try await Base44Client.shared.delete("WishlistItem", w.id)
            items.removeAll { $0.id == w.id }
            app.show("Moved \"\(w.title ?? "")\" to your collection")
            app.dataChanged()
        } catch { app.fail("Could not move", error) }
    }

    private func remove(_ w: WishlistItem) async {
        do {
            try await Base44Client.shared.delete("WishlistItem", w.id)
            items.removeAll { $0.id == w.id }
            app.show("Removed \"\(w.title ?? "")\" from wishlist")
        } catch { app.fail("Could not remove", error) }
    }
}
