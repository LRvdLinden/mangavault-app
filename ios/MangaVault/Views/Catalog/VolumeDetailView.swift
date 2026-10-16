import SwiftUI

/// In-memory caches, like the Maps the web app keeps for the volume page.
enum VolumeCache {
    static var manga: [String: Manga] = [:]
    static var chapters: [String: (date: Date, volumes: [String: [String]])] = [:]
    static var synopsis: [String: VolumeSynopsis] = [:]
    /// "mangaId:volume" → verified MangaFire URL ("" = checked, none found).
    static var mangaFire: [String: String] = [:]
}

struct VolumeDetailView: View {
    let mangaId: String
    @State private var volume: Int
    @Environment(AppState.self) private var app

    @State private var manga: Manga?
    @State private var loading = true
    @State private var cover = ""
    @State private var maxVolume = 1
    @State private var item: CollectionItem?
    @State private var wish: WishlistItem?
    @State private var busy = false
    @State private var wishBusy = false
    @State private var synopsis: VolumeSynopsis?
    @State private var synopsisLoading = false
    @State private var chapters: [String] = []
    @State private var editing: CollectionItem?
    @State private var mangaFireURL: URL?
    @State private var mangaFireLoading = false

    init(mangaId: String, volume: Int) {
        self.mangaId = mangaId
        _volume = State(initialValue: volume)
    }

    var body: some View {
        ScrollView {
            if loading {
                LoadingView()
            } else if let manga {
                VStack(alignment: .leading, spacing: 20) {
                    header(manga)
                    infoCard(icon: "book", title: "What Happens") {
                        if synopsisLoading { SkeletonLines() }
                        else { Text(nonEmpty(synopsis?.synopsis) ?? "No synopsis available for this volume.").bodyText() }
                    }
                    infoCard(icon: "clock.arrow.circlepath", title: volume == 1 ? "Introduction" : "Previously in the Story") {
                        if synopsisLoading { SkeletonLines() }
                        else { Text(nonEmpty(synopsis?.recap) ?? "Recap not available yet.").bodyText() }
                    }
                    infoCard(icon: "mappin.and.ellipse", title: "Location & Setting") {
                        if synopsisLoading { SkeletonLines() } else { locationContent }
                    }
                    overview(manga)
                    pager
                }
                .padding(16)
            } else {
                EmptyState(icon: "questionmark.square.dashed", text: "Manga not found.")
            }
        }
        .background(Theme.background)
        .navigationTitle("Volume \(volume)")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: volume) { await load() }
        .sheet(item: $editing) { it in
            CollectionItemEditor(item: it) { updated in item = updated }
        }
    }

    // MARK: Sections

    private func header(_ m: Manga) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Spacer()
                Color.clear.aspectRatio(2/3, contentMode: .fit).frame(width: 190)
                    .overlay {
                        if cover.isEmpty {
                            ZStack {
                                LinearGradient(colors: [Theme.primary.opacity(0.3), Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
                                VStack(spacing: 6) {
                                    Image(systemName: "book").font(.largeTitle).foregroundStyle(Theme.primary.opacity(0.6))
                                    Text("VOL. \(volume)").font(.caption.weight(.black)).foregroundStyle(Theme.muted)
                                    Text("Cover not available").font(.caption2).foregroundStyle(Theme.muted.opacity(0.7))
                                }
                            }
                        } else {
                            RemoteImage(url: cover)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
                    .shadow(color: .black.opacity(0.6), radius: 20, y: 10)
                Spacer()
            }
            HStack(spacing: 6) {
                if m.isShonenJump { Badge(text: "SHONEN JUMP") }
                if let p = nonEmpty(m.publisher) { Badge(text: p.uppercased(), fg: Theme.muted, bg: Theme.card, ring: Theme.border) }
                if let item { Badge(text: item.effectiveStatus.volumeLabel.uppercased(), fg: item.effectiveStatus == .on_hold ? .black : .white, bg: statusBg(item.effectiveStatus)) }
            }
            NavigationLink(value: Route.manga(m.id)) {
                Text(m.title).font(.system(size: 28, weight: .black)).tracking(-1).foregroundStyle(Theme.foreground)
                    .multilineTextAlignment(.leading)
            }
            .buttonStyle(.plain)
            Text("VOLUME \(volume)").font(.subheadline.weight(.black)).tracking(2).foregroundStyle(Theme.primary)
            if let g = nonEmpty(m.genre) { Text(g).font(.subheadline).foregroundStyle(Theme.muted) }

            HStack(spacing: 10) {
                if let item {
                    Button { Task { await remove() } } label: { Label("In Collection", systemImage: "checkmark.circle") }
                        .buttonStyle(PrimaryButtonStyle(color: Theme.secondary))
                        .disabled(busy)
                    Menu {
                        ForEach(ReadStatus.volumeCases) { s in
                            Button { Task { await setStatus(s) } } label: {
                                if s == item.effectiveStatus { Label(s.volumeLabel, systemImage: "checkmark") } else { Text(s.volumeLabel) }
                            }
                        }
                    } label: {
                        HStack(spacing: 4) { Text(item.effectiveStatus.volumeLabel); Image(systemName: "chevron.down").font(.caption) }
                            .font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
                            .padding(.horizontal, 12).frame(minHeight: 44)
                            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                    }
                    .disabled(busy)
                } else {
                    Button { Task { await add() } } label: { Label("Add to Collection", systemImage: "plus") }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(busy)
                }
                Button { Task { await toggleWish() } } label: {
                    Label(wish != nil ? "On Wishlist" : "Wishlist", systemImage: wish != nil ? "heart.fill" : "heart")
                }
                .buttonStyle(OutlineButtonStyle(color: wish != nil ? Theme.red : Theme.foreground, border: wish != nil ? Theme.red.opacity(0.5) : Theme.border))
                .disabled(wishBusy)
            }
            if let item {
                Button { editing = item } label: { Label("Rating, progress & notes", systemImage: "slider.horizontal.3") }
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.primary)
            }
            mangaFireRow
        }
    }

    @ViewBuilder private var mangaFireRow: some View {
        if mangaFireLoading {
            HStack(spacing: 6) {
                ProgressView().controlSize(.small).tint(Theme.muted)
                Text("Finding MangaFire link…")
            }
            .font(.caption).foregroundStyle(Theme.muted)
        } else if let url = mangaFireURL {
            Link(destination: url) {
                HStack(spacing: 6) {
                    Text("📖 Read On MangaFire")
                    Image(systemName: "arrow.up.right.square")
                }
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.primary)
                .padding(.horizontal, 14).frame(minHeight: 40)
                .background(Theme.primary.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.primary.opacity(0.4)))
            }
        } else {
            Label("MangaFire link unavailable for this manga", systemImage: "book")
                .font(.caption).foregroundStyle(Theme.muted)
        }
    }

    private func statusBg(_ s: ReadStatus) -> Color {
        switch s {
        case .plan_to_read: return Theme.secondary
        case .reading: return Theme.primary
        case .completed: return Theme.emerald
        case .on_hold: return Theme.amber
        case .dropped: return Theme.secondary
        }
    }

    private static let locationPlaceholder = "Location information not available yet."

    private static func cleanLocation(_ s: String?) -> String? {
        guard let v = nonEmpty(s), v != locationPlaceholder else { return nil }
        return v
    }

    @ViewBuilder private var locationContent: some View {
        let placeholder = Self.locationPlaceholder
        let loc: String? = Self.cleanLocation(synopsis?.location)
        let set: String? = Self.cleanLocation(synopsis?.setting)
        if loc == nil && set == nil {
            Text(placeholder).font(.subheadline).italic().foregroundStyle(Theme.muted)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                if let loc { Text(loc).font(.subheadline.weight(.bold)).foregroundStyle(Theme.primary) }
                if let set { Text(set).font(.subheadline).foregroundStyle(Theme.muted) }
            }
        }
    }

    private func infoCard<C: View>(icon: String, title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title.uppercased(), systemImage: icon)
                .font(.headline.weight(.black))
                .labelStyle(TintedIconLabelStyle())
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }

    private func overview(_ m: Manga) -> some View {
        var rows: [(String, String)] = [("Volume", "\(volume)")]
        if let g = nonEmpty(m.genre) { rows.append(("Genres", g)) }
        if let p = nonEmpty(m.publisher) { rows.append(("Publisher", p)) }
        if let l = m.languages, !l.isEmpty { rows.append(("Language", l.joined(separator: ", "))) }
        rows.append(("Total Volumes", "\(maxVolume)"))
        if !chapters.isEmpty {
            let chapterText: String
            if chapters.count > 3, let first = chapters.first, let last = chapters.last {
                chapterText = "\(first)–\(last) (\(chapters.count))"
            } else {
                chapterText = chapters.joined(separator: ", ")
            }
            rows.append(("Chapters Included", chapterText))
        }
        rows.append(("Price", m.priceValue.euro))
        if let item, item.chapter > 0 { rows.append(("Reading Progress", "Chapter \(item.chapter)")) }
        if let item, item.ratingValue > 0 { rows.append(("Your Rating", "\(item.ratingValue) / 10")) }
        let rowItems = rows
        let characters = (synopsis?.main_characters ?? []).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

        return VStack(alignment: .leading, spacing: 14) {
            Label("VOLUME OVERVIEW", systemImage: "books.vertical").font(.headline.weight(.black)).labelStyle(TintedIconLabelStyle())
            LazyVGrid(columns: [GridItem(.flexible(), alignment: .topLeading), GridItem(.flexible(), alignment: .topLeading)], alignment: .leading, spacing: 14) {
                ForEach(rowItems.indices, id: \.self) { idx in
                    let r = rowItems[idx]
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.0.uppercased()).font(.caption2.weight(.bold)).tracking(1).foregroundStyle(Theme.muted)
                        Text(r.1).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
                    }
                }
            }
            if !characters.isEmpty {
                Text("MAIN CHARACTERS").font(.caption2.weight(.bold)).tracking(1).foregroundStyle(Theme.muted)
                FlowLayout(spacing: 8) {
                    ForEach(characters, id: \.self) { c in
                        Text(c).font(.caption.weight(.bold)).padding(.horizontal, 10).padding(.vertical, 5)
                            .background(Theme.secondary, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }

    private var pager: some View {
        HStack(spacing: 12) {
            if volume > 1 {
                pagerButton(label: "Previous", title: "Volume \(volume - 1)", icon: "chevron.left", leading: true) { volume -= 1 }
            } else { Spacer().frame(maxWidth: .infinity) }
            if volume < maxVolume {
                pagerButton(label: "Next", title: "Volume \(volume + 1)", icon: "chevron.right", leading: false) { volume += 1 }
            } else { Spacer().frame(maxWidth: .infinity) }
        }
    }

    private func pagerButton(label: String, title: String, icon: String, leading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if leading { Image(systemName: icon).foregroundStyle(Theme.primary) }
                VStack(alignment: leading ? .leading : .trailing, spacing: 2) {
                    Text(label.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                    Text(title).font(.subheadline.weight(.black)).foregroundStyle(Theme.foreground)
                }
                .frame(maxWidth: .infinity, alignment: leading ? .leading : .trailing)
                if !leading { Image(systemName: icon).foregroundStyle(Theme.primary) }
            }
            .padding(14)
            .cardStyle()
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }

    // MARK: Data

    private func load() async {
        loading = manga == nil
        synopsis = nil; chapters = []; item = nil; wish = nil
        do {
            let m: Manga
            if let cached = VolumeCache.manga[mangaId] { m = cached }
            else { m = try await Base44Client.shared.get("Manga", mangaId, Manga.self); VolumeCache.manga[mangaId] = m }
            manga = m
            let stored = (m.volume_covers ?? []).filter { $0.volume > 0 && nonEmpty($0.url) != nil }
            let found = stored.first { Int($0.volume) == volume }
            let maxStored = stored.map { Int($0.volume) }.max() ?? 0
            maxVolume = maxStored > 0 ? maxStored : max(m.totalVolumes, 1)
            cover = found?.url ?? ""
            loading = false

            async let synT: Void = loadSynopsis(m)
            async let extraT: Void = loadExtras(m, storedCount: stored.count, hasCover: found != nil)
            async let userT: Void = loadUserState()
            async let fireT: Void = loadMangaFire(m)
            _ = await (synT, extraT, userT, fireT)
        } catch {
            if !(error is CancellationError) { app.fail("Could not load volume", error) }
            loading = false
        }
    }

    /// Same rules as the web app: a stored `mangaFireSlug` gives a per-volume URL
    /// (mangafire.to/title/<slug>/volume/<n>); otherwise ask `resolveMangaFireUrl`.
    private func loadMangaFire(_ m: Manga) async {
        let key = "\(m.id):\(volume)"
        let slug = (m.mangaFireSlug ?? "").trimmingCharacters(in: .whitespaces)
        if slug.range(of: "^[a-z0-9]{3,12}-[a-z0-9][a-z0-9-]*[a-z0-9]$", options: .regularExpression) != nil {
            let u = "https://mangafire.to/title/\(slug)/volume/\(volume)"
            mangaFireURL = URL(string: u); VolumeCache.mangaFire[key] = u; return
        }
        if let cached = VolumeCache.mangaFire[key] {
            mangaFireURL = cached.isEmpty ? nil : URL(string: cached); return
        }
        mangaFireLoading = true
        defer { mangaFireLoading = false }
        struct R: Decodable { let url: String?; let slug: String?; let volume_url: String?; let verified: Bool? }
        if let d = try? await Base44Client.shared.invoke("resolveMangaFireUrl", ["manga_id": m.id, "volume": volume]),
           let r = try? Base44Client.shared.decode(R.self, d), r.verified == true, let sl = nonEmpty(r.slug) {
            let u = nonEmpty(r.volume_url) ?? "https://mangafire.to/title/\(sl)/volume/\(volume)"
            mangaFireURL = URL(string: u)
            VolumeCache.mangaFire[key] = u
            var updated = m; updated.mangaFireSlug = sl; updated.mangaFireUrl = r.url
            VolumeCache.manga[m.id] = updated
        } else {
            mangaFireURL = nil
        }
    }

    private func loadExtras(_ m: Manga, storedCount: Int, hasCover: Bool) async {
        guard let mdx = nonEmpty(m.mangadex_id) else { return }
        if !hasCover {
            struct R: Decodable { let covers: [VolumeCover]? }
            if let d = try? await Base44Client.shared.invoke("getVolumeCovers", ["mangadex_id": mdx, "manga_id": m.id]),
               let r = try? Base44Client.shared.decode(R.self, d) {
                let all = (r.covers ?? []).filter { $0.volume > 0 && nonEmpty($0.url) != nil }
                if all.count > storedCount {
                    let mx = all.map { Int($0.volume) }.max() ?? 0
                    maxVolume = mx > 0 ? mx : max(m.totalVolumes, 1)
                    if let c = all.first(where: { Int($0.volume) == volume })?.url { cover = c }
                    var updated = m; updated.volume_covers = all
                    VolumeCache.manga[m.id] = updated
                }
            }
        }
        var vols: [String: [String]]
        if let c = VolumeCache.chapters[mdx], Date().timeIntervalSince(c.date) < 600 {
            vols = c.volumes
        } else {
            struct R: Decodable { let volumes: [String: [LooseString]]? }
            vols = [:]
            if let d = try? await Base44Client.shared.invoke("getVolumeChapters", ["mangadex_id": mdx]),
               let r = try? Base44Client.shared.decode(R.self, d) {
                vols = (r.volumes ?? [:]).mapValues { $0.map(\.value) }
                VolumeCache.chapters[mdx] = (Date(), vols)
            }
        }
        chapters = vols[String(volume)] ?? []
    }

    private func loadSynopsis(_ m: Manga) async {
        let key = "\(m.id):\(volume)"
        if let s = VolumeCache.synopsis[key] { synopsis = s; return }
        synopsisLoading = true
        defer { synopsisLoading = false }
        let payload: [String: Any] = ["title": m.title, "description": m.description ?? "", "genre": m.genre ?? "",
                                      "volume": volume, "total_volumes": m.totalVolumes]
        if let d = try? await Base44Client.shared.invoke("getVolumeSynopsis", payload),
           let s = try? Base44Client.shared.decode(VolumeSynopsis.self, d) {
            synopsis = s
            VolumeCache.synopsis[key] = s
        }
    }

    private func loadUserState() async {
        async let cT = Base44Client.shared.list("CollectionItem", CollectionItem.self, query: ["manga_id": mangaId], limit: 500)
        async let wT = Base44Client.shared.list("WishlistItem", WishlistItem.self, query: ["manga_id": mangaId], limit: 500)
        if let c = try? await cT { item = c.items.first { !$0.isOmnibus && $0.volume == volume } }
        if let w = try? await wT { wish = w.items.first { !$0.isOmnibus && $0.volume == volume } }
    }

    private func add() async {
        guard let m = manga, item == nil else { return }
        busy = true; defer { busy = false }
        do {
            let created = try await Base44Client.shared.create("CollectionItem",
                VaultService.collectionPayload(manga: m, volume: volume, volumeCover: cover, status: .plan_to_read), CollectionItem.self)
            item = created
            VaultService.log("added", mangaId: m.id, title: created.title, volume: volume)
            app.show("Added Vol. \(volume) to your collection"); app.dataChanged()
        } catch { app.fail("Could not add", error) }
    }

    private func remove() async {
        guard let it = item else { return }
        busy = true; defer { busy = false }
        do {
            try await Base44Client.shared.delete("CollectionItem", it.id)
            item = nil
            app.show("Removed Vol. \(volume)"); app.dataChanged()
        } catch { app.fail("Could not remove", error) }
    }

    private func setStatus(_ s: ReadStatus) async {
        guard var it = item, let m = manga else { return }
        busy = true; defer { busy = false }
        do {
            try await Base44Client.shared.update("CollectionItem", it.id, ["status": s.rawValue])
            it.status = s.rawValue
            item = it
            VaultService.log(s == .completed ? "completed" : "status", mangaId: m.id, title: it.title, volume: volume)
            app.show("Status updated"); app.dataChanged()
        } catch { app.fail("Update failed", error) }
    }

    private func toggleWish() async {
        guard let m = manga else { return }
        wishBusy = true; defer { wishBusy = false }
        if let w = wish {
            do {
                try await Base44Client.shared.delete("WishlistItem", w.id)
                wish = nil
                app.show("Removed Vol. \(volume) from wishlist"); app.dataChanged()
            } catch { app.fail("Could not remove", error) }
        } else {
            do {
                wish = try await Base44Client.shared.create("WishlistItem",
                    VaultService.wishlistPayload(manga: m, volume: volume, volumeCover: cover), WishlistItem.self)
                app.show("Added Vol. \(volume) to your wishlist"); app.dataChanged()
            } catch { app.fail("Could not add to wishlist", error) }
        }
    }
}

struct TintedIconLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 8) {
            configuration.icon.foregroundStyle(Theme.primary).font(.subheadline)
            configuration.title.foregroundStyle(Theme.foreground)
        }
    }
}

struct SkeletonLines: View {
    @State private var on = false
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach([1.0, 0.85, 0.65], id: \.self) { w in
                GeometryReader { g in
                    RoundedRectangle(cornerRadius: 4).fill(Theme.secondary).frame(width: g.size.width * CGFloat(w), height: 12)
                }
                .frame(height: 12)
            }
        }
        .opacity(on ? 0.5 : 1)
        .animation(.easeInOut(duration: 0.8).repeatForever(), value: on)
        .onAppear { on = true }
    }
}

extension Text {
    func bodyText() -> some View {
        self.font(.subheadline).foregroundStyle(Theme.foreground.opacity(0.9)).lineSpacing(3)
    }
}

/// Simple wrapping layout for tags.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, width: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > maxW, x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += size.width + spacing
            rowH = max(rowH, size.height)
            width = max(width, x - spacing)
        }
        return CGSize(width: proposal.width ?? width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowH = max(rowH, size.height)
        }
    }
}
