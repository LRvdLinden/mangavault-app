import SwiftUI

struct CatalogView: View {
    enum Mode { case catalog, discover }
    let mode: Mode

    @Environment(AppState.self) private var app
    @State private var items: [Manga] = []
    @State private var loading = true
    @State private var loadingMore = false
    @State private var cursor: String?
    @State private var hasMore = false
    @State private var search = ""
    @State private var debounced = ""
    @State private var filter = "all"
    @State private var owned: Set<String> = []
    @State private var currentQuery: [String: Any] = [:]
    @State private var surprise: Manga?
    @State private var errorText: String?

    private static let filters = ["all", "shonen_jump", "Kodansha", "Shogakukan", "Square Enix", "Kadokawa", "Shueisha", "Other"]
    private static let pageSize = 60

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if mode == .catalog {
                    EventBannerView().padding(.horizontal, 16).padding(.top, 12)
                }
                hero
                filterBar
                content.padding(16)
            }
        }
        .background(Theme.background)
        .navigationTitle(mode == .catalog ? "Catalog" : "Discover")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Wordmark(size: 18) }
            if mode == .catalog {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.discover) { Image(systemName: "safari") }
                }
            }
        }
        .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search manga...")
        .refreshable { await reload() }
        .task(id: search) {
            try? await Task.sleep(for: .milliseconds(250))
            if !Task.isCancelled { debounced = search }
        }
        .task(id: "\(debounced)|\(filter)") { await reload() }
        .task(id: app.dataVersion) { await loadOwned() }
        .navigationDestination(item: $surprise) { m in MangaDetailView(mangaId: m.id) }
    }

    // MARK: Header

    private var hero: some View {
        ZStack(alignment: .leading) {
            RadialGradient(colors: [Theme.primary.opacity(0.35), .clear], center: .top, startRadius: 0, endRadius: 320)
            VStack(alignment: .leading, spacing: 12) {
                Label(mode == .catalog ? "EVERY MANGA. EVERY LANGUAGE." : "DISCOVER",
                      systemImage: mode == .catalog ? "flame.fill" : "safari.fill")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.primary)
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(Theme.primary.opacity(0.1), in: Capsule())
                    .overlay(Capsule().stroke(Theme.primary.opacity(0.3)))
                (Text(mode == .catalog ? "THE MANGA " : "FIND YOUR NEXT ").foregroundStyle(Theme.foreground)
                 + Text(mode == .catalog ? "VAULT" : "READ").foregroundStyle(Theme.primary))
                    .font(.system(size: 36, weight: .black)).tracking(-1.5)
                Text(mode == .catalog
                     ? "The full MangaDex catalog, in English. Open a title to add the individual volumes you own, track your reading, and total your collection's value."
                     : "Browse the vault, filter by publisher, or get a surprise recommendation.")
                    .font(.subheadline).foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 16).padding(.vertical, 28)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.primary.opacity(0.1)).frame(height: 1) }
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if mode == .discover {
                    Button {
                        surprise = items.randomElement()
                    } label: {
                        Label("Surprise me", systemImage: "shuffle").font(.caption.weight(.bold))
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .foregroundStyle(Theme.primary)
                            .overlay(RoundedRectangle(cornerRadius: 9).stroke(Theme.primary.opacity(0.4)))
                    }
                    .buttonStyle(.plain)
                    .disabled(items.isEmpty)
                }
                ForEach(Self.filters, id: \.self) { f in
                    Chip(title: f == "all" ? "All" : (f == "shonen_jump" ? "Shonen Jump" : f), selected: filter == f) { filter = f }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
        }
        .background(Theme.background.opacity(0.9))
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.border).frame(height: 1) }
    }

    @ViewBuilder private var content: some View {
        if loading {
            LazyVGrid(columns: gridColumns, spacing: 16) {
                ForEach(0..<8, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 10).fill(Theme.card.opacity(0.6)).aspectRatio(2/3, contentMode: .fit)
                }
            }
            .redacted(reason: .placeholder)
        } else if let errorText, items.isEmpty {
            EmptyState(icon: "wifi.exclamationmark", text: errorText, buttonTitle: "Try again") { Task { await reload() } }
        } else if items.isEmpty {
            EmptyState(icon: "book.closed", text: "No manga found.")
        } else {
            LazyVGrid(columns: gridColumns, spacing: 20) {
                ForEach(items) { m in
                    NavigationLink(value: Route.manga(m.id)) {
                        MangaCard(manga: m, owned: mode == .discover && owned.contains(m.id), showGenre: mode == .discover)
                    }
                    .buttonStyle(.plain)
                    .onAppear { if m.id == items.last?.id { Task { await loadMore() } } }
                }
            }
            if hasMore {
                Button {
                    Task { await loadMore() }
                } label: {
                    Group { if loadingMore { ProgressView().tint(Theme.primary) } else { Text("Load More") } }
                        .frame(minWidth: 140)
                }
                .buttonStyle(OutlineButtonStyle(color: Theme.primary))
                .padding(.top, 24)
            }
        }
    }

    // MARK: Data

    private func titleQuery() -> [String: Any] {
        let s = debounced.trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? [:] : ["title": ["$regex": NSRegularExpression.escapedPattern(for: s), "$options": "i"]]
    }

    private func buildQuery() -> [String: Any] {
        var q = titleQuery()
        switch filter {
        case "all": q["is_shonen_jump"] = ["$ne": true]
        case "shonen_jump": q["is_shonen_jump"] = true
        default: q["publisher"] = filter
        }
        return q
    }

    private func reload() async {
        loading = true
        errorText = nil
        let q = buildQuery()
        currentQuery = q
        do {
            var head: [Manga] = []
            let rest: Page<Manga>
            if filter == "all" {
                var sjQuery = titleQuery(); sjQuery["is_shonen_jump"] = true
                let sj = sjQuery
                async let a = Base44Client.shared.list("Manga", Manga.self, query: sj, sort: "title", limit: 100, fields: Manga.listFields)
                async let b = Base44Client.shared.list("Manga", Manga.self, query: q, sort: "title", limit: Self.pageSize, fields: Manga.listFields)
                let pa = try await a
                head = pa.items
                rest = try await b
            } else {
                rest = try await Base44Client.shared.list("Manga", Manga.self, query: q, sort: "title", limit: Self.pageSize, fields: Manga.listFields)
            }
            guard !Task.isCancelled else { return }
            items = head + rest.items
            cursor = rest.next_cursor
            hasMore = rest.has_more ?? false
        } catch is CancellationError {
            return
        } catch {
            if (error as? URLError)?.code == .cancelled { return }
            errorText = error.localizedDescription
            items = []
        }
        loading = false
    }

    private func loadMore() async {
        guard hasMore, let cursor, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            let page = try await Base44Client.shared.list("Manga", Manga.self, query: currentQuery, sort: "title",
                                                          limit: Self.pageSize, fields: Manga.listFields, cursor: cursor)
            let known = Set(items.map(\.id))
            items += page.items.filter { !known.contains($0.id) }
            self.cursor = page.next_cursor
            hasMore = page.has_more ?? false
        } catch {
            app.fail("Could not load more", error)
        }
    }

    private func loadOwned() async {
        guard mode == .discover else { return }
        if let ids = try? await Base44Client.shared.distinct("CollectionItem", field: "manga_id") {
            owned = Set(ids)
        }
    }
}
