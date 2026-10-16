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
                    StatCard(icon: "eurosign.circle", label: "Total Value", value: items.reduce(0.0) { $0 + ($1.price ?? 0) }.euro, accent: Theme.red)
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

    private func card(_ w: WishlistItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            NavigationLink(value: w.isOmnibus || w.volume == nil ? Route.manga(w.manga_id ?? "") : Route.volume(mangaId: w.manga_id ?? "", volume: w.volume!)) {
                CoverView(info: CoverInfo(wish: w))
                    .overlay(alignment: .topTrailing) {
                        Badge(text: w.isOmnibus ? "3-IN-1" : "VOL. \(w.volume ?? 0)", icon: w.isOmnibus ? "books.vertical" : nil,
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
            _ = try await Base44Client.shared.create("CollectionItem", [
                "manga_id": w.manga_id ?? "",
                "title": w.title ?? "",
                "volume_number": w.volume.map { $0 as Any } ?? NSNull(),
                "volume_cover_url": w.volume_cover_url ?? "",
                "cover_image_url": w.cover_image_url ?? "",
                "cover_seed": w.cover_seed ?? (w.title ?? ""),
                "publisher": w.publisher ?? "",
                "price": w.price ?? 0,
                "is_shonen_jump": w.is_shonen_jump ?? false,
                "edition": w.edition ?? "standard",
                "volumes_included": w.volumes_included ?? "",
                "omnibus_of": w.omnibus_of ?? "",
                "read": false
            ], CollectionItem.self)
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
