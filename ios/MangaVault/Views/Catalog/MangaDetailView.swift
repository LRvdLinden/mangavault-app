import SwiftUI

struct MangaDetailView: View {
    let mangaId: String
    @Environment(AppState.self) private var app

    @State private var manga: Manga?
    @State private var loading = true
    @State private var owned: [String: String] = [:]     // volume key ("1", "2", … or "omnibus") → CollectionItem id
    @State private var wished: [String: String] = [:]    // same for WishlistItem
    @State private var covers: [VolumeCover] = []
    @State private var coversLoading = false
    @State private var busy: String?
    @State private var wishBusy: String?
    @State private var showFullDescription = false
    @State private var completedIds: Set<String> = []

    var body: some View {
        ScrollView {
            if loading {
                LoadingView()
            } else if let manga {
                VStack(alignment: .leading, spacing: 28) {
                    header(manga)
                    if !manga.isOmnibus { volumesSection(manga) }
                }
                .padding(16)
            } else {
                EmptyState(icon: "questionmark.square.dashed", text: "Manga not found.")
            }
        }
        .background(Theme.background)
        .navigationTitle(manga?.title ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let manga {
                ToolbarItem(placement: .topBarTrailing) {
                    ShareLink(item: Config.appBaseURL.appendingPathComponent("manga").appendingPathComponent(manga.id)) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
            }
        }
        .task { await load() }
        .refreshable { await load() }
    }

    // MARK: Header

    private func header(_ m: Manga) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Spacer()
                CoverView(info: CoverInfo(manga: m), cornerRadius: 14)
                    .frame(width: 190)
                    .shadow(color: .black.opacity(0.6), radius: 20, y: 10)
                Spacer()
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    if m.isOmnibus { Badge(text: "OMNIBUS · 3-IN-1", icon: "books.vertical", fg: Theme.primary, bg: Theme.background, ring: Theme.primary.opacity(0.5)) }
                    if m.isShonenJump { Badge(text: "SHONEN JUMP") }
                    if let p = nonEmpty(m.publisher) { Badge(text: p.uppercased(), fg: Theme.muted, bg: Theme.card, ring: Theme.border) }
                }
                Text(m.title).font(.system(size: 30, weight: .black)).tracking(-1).foregroundStyle(Theme.foreground)
                if m.isOmnibus, let of = nonEmpty(m.omnibus_of) {
                    (Text("Omnibus edition of ").foregroundStyle(Theme.muted) + Text(of).foregroundStyle(Theme.primary).bold()
                     + Text(" · \(m.volumes_included ?? "")").foregroundStyle(Theme.muted)).font(.subheadline)
                }
                if let g = nonEmpty(m.genre) { Text(g).font(.subheadline).foregroundStyle(Theme.muted) }
                if let d = nonEmpty(m.description) {
                    Text(d).font(.subheadline).foregroundStyle(Theme.muted)
                        .lineLimit(showFullDescription ? nil : 4)
                        .onTapGesture { withAnimation { showFullDescription.toggle() } }
                }
            }

            HStack(spacing: 28) {
                stat(m.isOmnibus ? "Volumes Included" : "Volumes",
                     m.isOmnibus ? (nonEmpty(m.volumes_included) ?? "\(m.totalVolumes)") : "\(volumeCount(m))")
                stat("Price", m.priceValue.euro, color: Theme.primary)
                if !m.isOmnibus { stat("In Collection", "\(owned.count) / \(volumeCount(m))") }
            }

            if !m.isOmnibus && !owned.isEmpty { seriesProgress(m) }

            if m.isOmnibus {
                HStack(spacing: 10) {
                    Button { Task { await toggleOmnibusCollection(m) } } label: {
                        Label(owned["omnibus"] != nil ? "In Your Collection" : "Add Omnibus to Collection",
                              systemImage: owned["omnibus"] != nil ? "checkmark.circle" : "plus")
                    }
                    .buttonStyle(PrimaryButtonStyle(color: owned["omnibus"] != nil ? Theme.secondary : Theme.primary))
                    .disabled(busy == "omnibus")

                    Button { Task { await toggleOmnibusWishlist(m) } } label: {
                        Label(wished["omnibus"] != nil ? "On Wishlist" : "Add to Wishlist",
                              systemImage: wished["omnibus"] != nil ? "heart.fill" : "heart")
                    }
                    .buttonStyle(OutlineButtonStyle(color: wished["omnibus"] != nil ? Theme.red : Theme.foreground,
                                                    border: wished["omnibus"] != nil ? Theme.red.opacity(0.5) : Theme.border))
                    .disabled(wishBusy == "omnibus")
                }
            } else {
                Label("Add the volumes you own below · tap the heart to save to your wishlist.", systemImage: "book")
                    .font(.caption).foregroundStyle(Theme.muted)
            }
        }
    }

    /// "Series Progress" card (owned vs. read), new in the web app.
    private func seriesProgress(_ m: Manga) -> some View {
        let total = volumeCount(m)
        let ownedCount = owned.count
        let readCount = owned.values.filter { completedIds.contains($0) }.count
        let collPct = total > 0 ? min(100, Int((Double(ownedCount) / Double(total) * 100).rounded())) : 0
        let readPct = ownedCount > 0 ? Int((Double(readCount) / Double(ownedCount) * 100).rounded()) : 0
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Series Progress", systemImage: "books.vertical").font(.subheadline.weight(.black)).labelStyle(TintedIconLabelStyle())
                Spacer()
                NavigationLink(value: Route.series) { Text("All series").font(.caption.weight(.bold)).foregroundStyle(Theme.primary) }
            }
            HStack(spacing: 14) {
                (Text("Owned: ").foregroundStyle(Theme.muted) + Text("\(ownedCount) / \(total)").bold())
                (Text(Image(systemName: "checkmark.circle")).foregroundStyle(Theme.emerald) + Text(" Read: ").foregroundStyle(Theme.muted) + Text("\(readCount)").bold())
                (Text("Unread: ").foregroundStyle(Theme.muted) + Text("\(ownedCount - readCount)").bold())
            }
            .font(.caption)
            ProgressBar(value: Double(collPct) / 100, height: 6)
            ProgressBar(value: Double(readPct) / 100, height: 6, color: Theme.emerald)
            HStack {
                Text("Collection \(collPct)%")
                Spacer()
                Text("Read \(readPct)%")
            }
            .font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
        }
        .padding(14)
        .cardStyle()
    }

    private func stat(_ label: String, _ value: String, color: Color = Theme.foreground) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(.caption2.weight(.bold)).tracking(1).foregroundStyle(Theme.muted)
            Text(value).font(.title2.weight(.black)).foregroundStyle(color)
        }
    }

    // MARK: Volumes

    private func volumeCount(_ m: Manga) -> Int { covers.isEmpty ? max(m.totalVolumes, 1) : covers.count }

    private func coverURL(_ v: Int) -> String {
        covers.first { Int($0.volume) == v }?.url ?? ""
    }

    private func volumesSection(_ m: Manga) -> some View {
        let count = volumeCount(m)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "book.fill").foregroundStyle(Theme.primary)
                Text("ALL VOLUMES").font(.title3.weight(.black))
                if coversLoading { ProgressView().tint(Theme.primary).controlSize(.small) }
            }
            Text("\(owned.count) of \(count) in collection · \(wished.count) on wishlist")
                .font(.footnote.weight(.bold)).foregroundStyle(Theme.muted)

            LazyVGrid(columns: smallGridColumns, spacing: 14) {
                ForEach(1...count, id: \.self) { v in
                    volumeTile(m, v)
                }
            }
        }
    }

    private func volumeTile(_ m: Manga, _ v: Int) -> some View {
        let key = String(v)
        let isOwned = owned[key] != nil
        let isWished = wished[key] != nil
        let url = coverURL(v)
        return VStack(spacing: 6) {
            NavigationLink(value: Route.volume(mangaId: m.id, volume: v)) {
                Color.clear.aspectRatio(2/3, contentMode: .fit)
                    .overlay {
                        if url.isEmpty {
                            LinearGradient(colors: [Theme.primary.opacity(0.3), Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
                        } else {
                            RemoteImage(url: url)
                        }
                    }
                    .overlay(alignment: .bottom) {
                        ZStack(alignment: .bottom) {
                            LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom).frame(height: 50)
                            Text("VOL. \(v)").font(.system(size: 10, weight: .black)).tracking(1.5).foregroundStyle(.white).padding(.bottom, 6)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(isOwned ? Theme.primary : (isWished ? Theme.red.opacity(0.6) : Theme.border), lineWidth: isOwned ? 2 : 1))
            }
            .buttonStyle(.plain)
            .overlay(alignment: .topTrailing) {
                circleButton(icon: isOwned ? "checkmark" : "plus", active: isOwned, color: Theme.primary, loading: busy == key) {
                    Task { await toggleVolume(m, v) }
                }
                .padding(6)
            }
            .overlay(alignment: .topLeading) {
                circleButton(icon: isWished ? "heart.fill" : "heart", active: isWished, color: Theme.red, loading: wishBusy == key) {
                    Task { await toggleWish(m, v) }
                }
                .padding(6)
            }
        }
    }

    private func circleButton(icon: String, active: Bool, color: Color, loading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(active ? color : Theme.background.opacity(0.75))
                Circle().stroke(active ? .clear : color.opacity(0.5))
                if loading { ProgressView().controlSize(.mini).tint(.white) }
                else { Image(systemName: icon).font(.system(size: 12, weight: .bold)).foregroundStyle(active ? .white : color) }
            }
            .frame(width: 30, height: 30)
            .shadow(radius: 4)
        }
        .buttonStyle(.plain)
        .disabled(loading)
    }

    // MARK: Data

    private struct VolRef: Decodable { let id: String; let volume_number: Double?; let edition: String?; let status: String?; let read: Bool? }

    private static func map(_ refs: [VolRef]) -> [String: String] {
        var m: [String: String] = [:]
        for r in refs {
            if r.edition == "omnibus" { if m["omnibus"] == nil { m["omnibus"] = r.id } }
            else if let v = r.volume_number { let k = String(Int(v)); if m[k] == nil { m[k] = r.id } }
        }
        return m
    }

    private func load() async {
        do {
            async let mT = Base44Client.shared.get("Manga", mangaId, Manga.self)
            async let cT = Base44Client.shared.list("CollectionItem", VolRef.self, query: ["manga_id": mangaId], limit: 500, fields: ["id", "volume_number", "edition", "status", "read"])
            async let wT = Base44Client.shared.list("WishlistItem", VolRef.self, query: ["manga_id": mangaId], limit: 500, fields: ["id", "volume_number", "edition"])
            let m = try await mT
            let c = try await cT
            let w = try await wT
            manga = m
            owned = Self.map(c.items)
            completedIds = Set(c.items.filter { ($0.status ?? (($0.read ?? false) ? "completed" : "plan_to_read")) == "completed" }.map(\.id))
            wished = Self.map(w.items)
            loading = false
            if !m.isOmnibus {
                coversLoading = (m.volume_covers ?? []).isEmpty && m.mangadex_id != nil
                covers = await VaultService.volumeCovers(for: m)
                coversLoading = false
            }
        } catch {
            if !(error is CancellationError) { app.fail("Could not load manga", error) }
            loading = false
        }
    }

    private func toggleVolume(_ m: Manga, _ v: Int) async {
        let key = String(v)
        busy = key; defer { busy = nil }
        if let id = owned[key] {
            do {
                try await Base44Client.shared.delete("CollectionItem", id)
                owned[key] = nil
                app.show("Removed Vol. \(v)")
                app.dataChanged()
            } catch { app.fail("Could not remove", error) }
        } else {
            do {
                let item = try await Base44Client.shared.create("CollectionItem",
                    VaultService.collectionPayload(manga: m, volume: v, volumeCover: coverURL(v)), CollectionItem.self)
                owned[key] = item.id
                VaultService.log("added", mangaId: m.id, title: item.title, volume: v)
                app.show("Added Vol. \(v) to your collection")
                app.dataChanged()
            } catch { app.fail("Could not add", error) }
        }
    }

    private func toggleWish(_ m: Manga, _ v: Int) async {
        let key = String(v)
        wishBusy = key; defer { wishBusy = nil }
        if let id = wished[key] {
            do {
                try await Base44Client.shared.delete("WishlistItem", id)
                wished[key] = nil
                app.show("Removed Vol. \(v) from wishlist")
                app.dataChanged()
            } catch { app.fail("Could not remove", error) }
        } else {
            do {
                let item = try await Base44Client.shared.create("WishlistItem",
                    VaultService.wishlistPayload(manga: m, volume: v, volumeCover: coverURL(v)), WishlistItem.self)
                wished[key] = item.id
                app.show("Added Vol. \(v) to your wishlist")
                app.dataChanged()
            } catch { app.fail("Could not add to wishlist", error) }
        }
    }

    private func toggleOmnibusCollection(_ m: Manga) async {
        busy = "omnibus"; defer { busy = nil }
        if let id = owned["omnibus"] {
            do {
                try await Base44Client.shared.delete("CollectionItem", id)
                owned["omnibus"] = nil
                app.show("Removed \"\(m.title)\""); app.dataChanged()
            } catch { app.fail("Could not remove", error) }
        } else {
            do {
                let item = try await Base44Client.shared.create("CollectionItem",
                    VaultService.collectionPayload(manga: m, volume: nil, volumeCover: ""), CollectionItem.self)
                owned["omnibus"] = item.id
                VaultService.log("added", mangaId: m.id, title: m.title, volume: nil, meta: "omnibus")
                app.show("Added \"\(m.title)\" to your collection"); app.dataChanged()
            } catch { app.fail("Could not add", error) }
        }
    }

    private func toggleOmnibusWishlist(_ m: Manga) async {
        wishBusy = "omnibus"; defer { wishBusy = nil }
        if let id = wished["omnibus"] {
            do {
                try await Base44Client.shared.delete("WishlistItem", id)
                wished["omnibus"] = nil
                app.show("Removed \"\(m.title)\" from wishlist"); app.dataChanged()
            } catch { app.fail("Could not remove", error) }
        } else {
            do {
                let item = try await Base44Client.shared.create("WishlistItem",
                    VaultService.wishlistPayload(manga: m, volume: nil, volumeCover: ""), WishlistItem.self)
                wished["omnibus"] = item.id
                app.show("Added \"\(m.title)\" to your wishlist"); app.dataChanged()
            } catch { app.fail("Could not add", error) }
        }
    }
}
