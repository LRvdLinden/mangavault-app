import SwiftUI

/// Shared mutations on collection items, with the same activity logging as the web app.
@MainActor
enum CollectionActions {
    static func update(_ app: AppState, _ item: CollectionItem, _ data: [String: Any], toast: String?) async -> Bool {
        do {
            try await Base44Client.shared.update("CollectionItem", item.id, data)
            if let toast { app.show(toast) }
            app.dataChanged()
            return true
        } catch {
            app.fail("Update failed", error)
            return false
        }
    }

    static func setStatus(_ app: AppState, _ item: CollectionItem, _ s: ReadStatus) async -> Bool {
        let ok = await update(app, item, ["status": s.rawValue], toast: "Status updated")
        if ok {
            VaultService.log("status", mangaId: item.manga_id, title: item.title, volume: item.volume)
            if s == .completed { VaultService.log("completed", mangaId: item.manga_id, title: item.title, volume: item.volume) }
        }
        return ok
    }

    static func setRating(_ app: AppState, _ item: CollectionItem, _ r: Int) async -> Bool {
        let ok = await update(app, item, ["rating": r], toast: "Rating updated")
        if ok { VaultService.log("rated", mangaId: item.manga_id, title: item.title, volume: item.volume) }
        return ok
    }

    static func toggleFavorite(_ app: AppState, _ item: CollectionItem) async -> Bool {
        let ok = await update(app, item, ["favorite": !item.isFavorite],
                              toast: item.isFavorite ? "Removed from favorites" : "Added to favorites")
        if ok { VaultService.log("favorite", mangaId: item.manga_id, title: item.title, volume: item.volume) }
        return ok
    }

    static func setChapter(_ app: AppState, _ item: CollectionItem, _ ch: Int) async -> String? {
        let today = DayKey.local()
        let ok = await update(app, item, ["current_chapter": ch, "last_read_date": today], toast: "Progress updated")
        if ok { VaultService.log("progress", mangaId: item.manga_id, title: item.title, volume: item.volume, meta: "ch.\(ch)") }
        return ok ? today : nil
    }

    static func setNotes(_ app: AppState, _ item: CollectionItem, _ notes: String) async -> Bool {
        await update(app, item, ["notes": notes], toast: "Note saved")
    }

    static func remove(_ app: AppState, _ item: CollectionItem) async -> Bool {
        do {
            try await Base44Client.shared.delete("CollectionItem", item.id)
            app.show("Removed \"\(item.titleText)\"")
            app.dataChanged()
            return true
        } catch {
            app.fail("Remove failed", error)
            return false
        }
    }
}

struct CollectionView: View {
    @Environment(AppState.self) private var app
    @State private var items: [CollectionItem] = []
    @State private var loading = true
    @State private var search = ""
    @State private var statusFilter: ReadStatus?
    @State private var favoritesOnly = false
    @State private var sort = "recent"
    @State private var gridMode = true
    @State private var editing: CollectionItem?
    @State private var lastLoadedVersion = -1
    @State private var mangaInfo: [String: (genre: String, total: Int)] = [:]
    @State private var genreFilter: Set<String> = []
    @State private var ratingFilter = "all"      // all · unrated · 5 · 4 · 3 · 2 · 1
    @State private var progressFilter = "all"    // all · unread · partial · done
    @State private var showSeries = false
    @State private var errorText: String?

    static let sorts: [(key: String, label: String)] = [
        ("recent", "Recently Added"), ("updated", "Last Updated"), ("title", "Title A-Z"), ("title_desc", "Title Z-A"),
        ("progress", "Reading Progress"), ("rating", "Highest Rating"), ("volume", "Volume Number"),
        ("owned_asc", "Owned Volumes ↑"), ("owned_desc", "Owned Volumes ↓"), ("total_asc", "Total Volumes ↑"), ("total_desc", "Total Volumes ↓")
    ]

    private static func genres(_ g: String?) -> [String] {
        (g ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).lowercased() }.filter { !$0.isEmpty }
    }

    private var allGenres: [String] {
        var set = Set<String>()
        for v in mangaInfo.values { Self.genres(v.genre).forEach { set.insert($0) } }
        return set.sorted()
    }

    private var filtersActive: Bool {
        !genreFilter.isEmpty || ratingFilter != "all" || progressFilter != "all" || statusFilter != nil || favoritesOnly || !search.isEmpty
    }

    private func clearFilters() {
        genreFilter = []; ratingFilter = "all"; progressFilter = "all"; statusFilter = nil; favoritesOnly = false; search = ""
    }

    /// Same filtering & sorting rules as the web collection page.
    private var filtered: [CollectionItem] {
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        var r = items.filter { i in
            guard !q.isEmpty else { return true }
            let fields: [String] = [i.titleText, i.publisher ?? "", i.notes ?? "", i.volumes_included ?? "", i.volume.map { "vol \($0)" } ?? ""]
            return fields.contains { $0.lowercased().contains(q) }
        }
        if let statusFilter { r = r.filter { $0.effectiveStatus == statusFilter } }
        if favoritesOnly { r = r.filter { $0.isFavorite } }
        if !genreFilter.isEmpty {
            r = r.filter { i in
                let g = Self.genres(mangaInfo[i.manga_id ?? ""]?.genre)
                return genreFilter.contains { g.contains($0) }
            }
        }
        if ratingFilter == "unrated" { r = r.filter { ($0.rating ?? 0) <= 0 } }
        else if let min = Double(ratingFilter) { r = r.filter { ($0.rating ?? 0) >= min } }
        switch progressFilter {
        case "done": r = r.filter { $0.effectiveStatus == .completed }
        case "unread": r = r.filter { $0.effectiveStatus != .completed && $0.chapter <= 0 }
        case "partial": r = r.filter { $0.effectiveStatus != .completed && ($0.chapter > 0 || $0.effectiveStatus == .reading) }
        default: break
        }
        var ownedPerManga: [String: Int] = [:]
        items.forEach { ownedPerManga[$0.manga_id ?? "", default: 0] += 1 }
        let ownedCount: (CollectionItem) -> Int = { i in ownedPerManga[i.manga_id ?? ""] ?? 0 }
        let total: (CollectionItem) -> Int = { i in
            let t = self.mangaInfo[i.manga_id ?? ""]?.total ?? 0
            return t > 0 ? t : Int(i.total_volumes ?? 0)
        }
        let byTotal: (Int) -> (CollectionItem, CollectionItem) -> Bool = { dir in
            { a, b in
                let ta = total(a), tb = total(b)
                if ta == 0 || tb == 0 { return ta != 0 && tb == 0 }   // unknown totals last
                return dir > 0 ? ta < tb : ta > tb
            }
        }
        switch sort {
        case "updated":
            let stamp: (CollectionItem) -> String = { i in i.last_read_date ?? i.updated_date ?? "" }
            r.sort { stamp($0) > stamp($1) }
        case "title": r.sort { a, b in
            let c = a.titleText.localizedCompare(b.titleText)
            return c == .orderedSame ? (a.volume ?? 0) < (b.volume ?? 0) : c == .orderedAscending
        }
        case "title_desc": r.sort { $0.titleText.localizedCompare($1.titleText) == .orderedDescending }
        case "progress": r.sort { $0.chapter > $1.chapter }
        case "rating": r.sort { ($0.rating ?? 0) > ($1.rating ?? 0) }
        case "volume": r.sort { ($0.volume ?? 0) < ($1.volume ?? 0) }
        case "owned_asc": r.sort { ownedCount($0) < ownedCount($1) }
        case "owned_desc": r.sort { ownedCount($0) > ownedCount($1) }
        case "total_asc": r.sort(by: byTotal(1))
        case "total_desc": r.sort(by: byTotal(-1))
        default: r.sort { ($0.created_date ?? "") > ($1.created_date ?? "") }
        }
        return r
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(lead: "MY", accent: "COLLECTION", subtitle: "Your personal manga vault, volume by volume.")
                stats
                filters
                if !loading && errorText == nil { seriesPanel }
                if let errorText {
                    VStack(spacing: 10) {
                        Text("Could not load your collection: \(errorText)").font(.subheadline).foregroundStyle(Theme.muted)
                        Button("Try again") { Task { await load() } }.buttonStyle(PrimaryButtonStyle())
                    }
                    .frame(maxWidth: .infinity).padding(20).cardStyle()
                } else {
                    content
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Collection")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, prompt: "Search your collection...")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Menu {
                    Picker("Sort", selection: $sort) {
                        ForEach(Self.sorts.indices, id: \.self) { idx in
                            Text(Self.sorts[idx].label).tag(Self.sorts[idx].key)
                        }
                    }
                } label: { Image(systemName: "arrow.up.arrow.down") }
                Button { gridMode.toggle() } label: { Image(systemName: gridMode ? "list.bullet" : "square.grid.2x2") }
            }
        }
        .refreshable { await load() }
        .task(id: app.dataVersion) {
            // Reload when another screen changed data (but not after our own optimistic updates).
            if app.dataVersion != lastLoadedVersion { await load() }
        }
        .sheet(item: $editing) { it in
            CollectionItemEditor(item: it) { updated in
                if let i = items.firstIndex(where: { $0.id == updated.id }) { items[i] = updated }
            }
        }
    }

    private var stats: some View {
        let completed = items.filter { $0.effectiveStatus == .completed }.count
        let value = items.reduce(0.0) { $0 + ($1.price ?? 0) }
        let rated = items.filter { ($0.rating ?? 0) > 0 }
        let avg = rated.isEmpty ? "—" : String(format: "%.1f", rated.reduce(0.0) { $0 + ($1.rating ?? 0) } / Double(rated.count))
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            StatCard(icon: "books.vertical", label: "Volumes", value: "\(items.count)")
            StatCard(icon: "checkmark.circle", label: "Completed", value: "\(completed)", accent: Theme.emerald)
            StatCard(icon: "eurosign.circle", label: "Total Value", value: value.euro)
            StatCard(icon: "star", label: "Avg Rating", value: avg, accent: Theme.orange)
            StatCard(icon: "heart", label: "Favorites", value: "\(items.filter { $0.isFavorite }.count)", accent: Theme.red)
        }
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Chip(title: "All", selected: statusFilter == nil) { statusFilter = nil }
                    ForEach(ReadStatus.allCases) { s in
                        Chip(title: s.label, selected: statusFilter == s) { statusFilter = s }
                    }
                    Chip(title: "Favorites", icon: favoritesOnly ? "heart.fill" : "heart", selected: favoritesOnly, selectedColor: Theme.red) {
                        favoritesOnly.toggle()
                    }
                }
            }
            HStack(spacing: 8) {
                Menu {
                    Picker("Rating", selection: $ratingFilter) {
                        Text("Any rating").tag("all")
                        Text("Not rated").tag("unrated")
                        ForEach([5, 4, 3, 2, 1], id: \.self) { n in Text(n == 5 ? "5 stars" : "\(n)+ stars").tag(String(n * 2)) }
                    }
                } label: { filterPill(ratingFilter == "all" ? "Any rating" : (ratingFilter == "unrated" ? "Not rated" : "\((Int(ratingFilter) ?? 0) / 2)+ stars"), active: ratingFilter != "all") }
                Menu {
                    Picker("Progress", selection: $progressFilter) {
                        Text("Any progress").tag("all")
                        Text("Not started (0%)").tag("unread")
                        Text("Partially read").tag("partial")
                        Text("Completed (100%)").tag("done")
                    }
                } label: { filterPill(progressLabel, active: progressFilter != "all") }
                if filtersActive {
                    Button { clearFilters() } label: { Label("Clear filters", systemImage: "xmark").font(.caption.weight(.bold)) }
                        .foregroundStyle(Theme.primary)
                }
            }
            if !allGenres.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(allGenres, id: \.self) { g in
                            Chip(title: g, selected: genreFilter.contains(g)) {
                                if genreFilter.contains(g) { genreFilter.remove(g) } else { genreFilter.insert(g) }
                            }
                        }
                    }
                }
            }
        }
    }

    private var progressLabel: String {
        switch progressFilter {
        case "unread": return "Not started (0%)"
        case "partial": return "Partially read"
        case "done": return "Completed (100%)"
        default: return "Any progress"
        }
    }

    private func filterPill(_ text: String, active: Bool) -> some View {
        HStack(spacing: 4) {
            Text(text)
            Image(systemName: "chevron.down").font(.caption2)
        }
        .font(.caption.weight(.bold))
        .padding(.horizontal, 10).padding(.vertical, 7)
        .foregroundStyle(active ? Color.white : Theme.foreground)
        .background(active ? Theme.primary : Theme.card, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(active ? Color.clear : Theme.border))
    }

    /// Collapsible "Series progress" overview (web: CollectionSeriesProgress).
    private struct SeriesRow: Identifiable {
        let id: String; let title: String; let total: Int
        var owned = 0, read = 0, current = 0
        var updated = ""
    }

    private var seriesRows: [SeriesRow] {
        var map: [String: SeriesRow] = [:]
        for i in items {
            guard let mid = i.manga_id else { continue }
            var row = map[mid] ?? SeriesRow(id: mid, title: i.titleText, total: mangaInfo[mid]?.total ?? Int(i.total_volumes ?? 0))
            row.owned += 1
            if i.effectiveStatus == .completed { row.read += 1 }
            if i.effectiveStatus == .reading, (i.volume ?? 0) > row.current { row.current = i.volume ?? 0 }
            let upd = nonEmpty(i.last_read_date) ?? String((i.updated_date ?? "").prefix(10))
            if upd > row.updated { row.updated = upd }
            map[mid] = row
        }
        return map.values.sorted { $0.updated > $1.updated }
    }

    private func seriesSummary(_ r: SeriesRow) -> String {
        var s = "\(r.read) read · \(r.owned) owned"
        if r.total > 0 { s += " / \(r.total) vols" }
        if r.current > 0 { s += " · on vol. \(r.current)" }
        return s
    }

    private func seriesFooter(_ r: SeriesRow) -> String {
        var s: String
        if r.total > 0 {
            let pct = min(100, Int((Double(r.read) / Double(r.total) * 100).rounded()))
            s = "\(pct)% complete"
        } else {
            s = "\(r.read) volumes read"
        }
        if !r.updated.isEmpty { s += " · updated \(r.updated)" }
        return s
    }

    @ViewBuilder private var seriesPanel: some View {
        let rows = seriesRows
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Button { withAnimation { showSeries.toggle() } } label: {
                    HStack {
                        Label("Series progress (\(rows.count))", systemImage: "books.vertical").font(.subheadline.weight(.bold))
                        Spacer()
                        Image(systemName: "chevron.down").rotationEffect(.degrees(showSeries ? 180 : 0))
                    }
                    .foregroundStyle(Theme.foreground)
                }
                .buttonStyle(.plain)
                if showSeries {
                    ForEach(rows) { r in
                        NavigationLink(value: Route.manga(r.id)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(r.title).font(.subheadline.weight(.bold)).lineLimit(1)
                                Text(seriesSummary(r))
                                    .font(.caption).foregroundStyle(Theme.muted)
                                if r.total > 0 { ProgressBar(value: Double(r.read) / Double(r.total), height: 5) }
                                Text(seriesFooter(r))
                                    .font(.caption2).foregroundStyle(Theme.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .background(Theme.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(14)
            .cardStyle()
        }
    }

    @ViewBuilder private var content: some View {
        if loading && items.isEmpty {
            LazyVGrid(columns: gridColumns, spacing: 16) {
                ForEach(0..<6, id: \.self) { _ in RoundedRectangle(cornerRadius: 10).fill(Theme.card.opacity(0.6)).aspectRatio(2/3, contentMode: .fit) }
            }
        } else if filtered.isEmpty {
            if items.isEmpty {
                EmptyState(icon: "books.vertical", text: "Your collection is empty.", buttonTitle: "Browse catalog") { app.selectTab(.catalog) }
            } else {
                EmptyState(icon: "books.vertical", text: "No volumes match your filters.", buttonTitle: "Clear filters") { clearFilters() }
            }
        } else if gridMode {
            LazyVGrid(columns: gridColumns, spacing: 22) {
                ForEach(filtered) { item in gridCard(item) }
            }
        } else {
            LazyVStack(spacing: 10) {
                ForEach(filtered) { item in listRow(item) }
            }
        }
    }

    private func route(_ i: CollectionItem) -> Route {
        if i.isOmnibus || i.volume == nil { return .manga(i.manga_id ?? "") }
        return .volume(mangaId: i.manga_id ?? "", volume: i.volume!)
    }

    private func gridCard(_ item: CollectionItem) -> some View {
        let st = item.effectiveStatus
        return VStack(alignment: .leading, spacing: 6) {
            NavigationLink(value: route(item)) {
                CoverView(info: CoverInfo(item: item))
                    .overlay(alignment: .topTrailing) {
                        HStack(spacing: 3) {
                            Circle().fill(Theme.statusColor(st)).frame(width: 6, height: 6)
                            Text(st.label)
                        }
                        .font(.system(size: 9, weight: .black))
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .foregroundStyle(Theme.statusColor(st))
                        .background(.black.opacity(0.65), in: RoundedRectangle(cornerRadius: 4))
                        .padding(8)
                    }
                    .overlay(alignment: .bottomLeading) {
                        if item.chapter > 0 {
                            Text("Ch. \(item.chapter)").font(.system(size: 9, weight: .black)).foregroundStyle(Theme.primary)
                                .padding(.horizontal, 6).padding(.vertical, 3)
                                .background(Theme.background.opacity(0.85), in: RoundedRectangle(cornerRadius: 4))
                                .padding(8)
                        }
                    }
            }
            .buttonStyle(.plain)
            .overlay(alignment: .topLeading) {
                Button { Task { await toggleFavorite(item) } } label: {
                    Image(systemName: item.isFavorite ? "heart.fill" : "heart")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(item.isFavorite ? Theme.red : Theme.muted)
                        .frame(width: 30, height: 30)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
                .padding(6)
            }
            .contextMenu { itemMenu(item) }

            Text(item.titleText).font(.subheadline.weight(.bold)).lineLimit(1)
            HStack {
                Text(item.volumeLabel).font(.caption).foregroundStyle(Theme.muted)
                Spacer()
                Text((item.price ?? 0).euro).font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
            }
            HStack {
                StarRating(value: item.ratingValue) { r in Task { await setRating(item, r) } }
                Spacer()
                statusMenu(item)
            }
            Button { editing = item } label: {
                Text("Details").frame(maxWidth: .infinity).font(.caption.weight(.bold))
            }
            .buttonStyle(OutlineButtonStyle(color: Theme.primary))
            .controlSize(.small)
        }
    }

    private func statusMenu(_ item: CollectionItem) -> some View {
        Menu {
            ForEach(ReadStatus.allCases) { s in
                Button { Task { await setStatus(item, s) } } label: {
                    if s == item.effectiveStatus { Label(s.label, systemImage: "checkmark") } else { Text(s.label) }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle").font(.system(size: 18)).foregroundStyle(Theme.muted)
        }
    }

    @ViewBuilder private func itemMenu(_ item: CollectionItem) -> some View {
        Menu("Status") {
            ForEach(ReadStatus.allCases) { s in
                Button(s.label) { Task { await setStatus(item, s) } }
            }
        }
        Button { editing = item } label: { Label("Rating, progress & notes", systemImage: "slider.horizontal.3") }
        Button { Task { await toggleFavorite(item) } } label: {
            Label(item.isFavorite ? "Remove favorite" : "Add favorite", systemImage: item.isFavorite ? "heart.slash" : "heart")
        }
        Button(role: .destructive) { Task { await remove(item) } } label: { Label("Remove", systemImage: "trash") }
    }

    private func listRow(_ item: CollectionItem) -> some View {
        let st = item.effectiveStatus
        return HStack(spacing: 12) {
            NavigationLink(value: route(item)) {
                CoverView(info: CoverInfo(item: item), cornerRadius: 6).frame(width: 52)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                Text(item.titleText).font(.subheadline.weight(.bold)).lineLimit(1)
                Text("\(item.volumeLabel) · \((item.price ?? 0).euro)\(item.chapter > 0 ? " · Ch. \(item.chapter)" : "")")
                    .font(.caption).foregroundStyle(Theme.muted)
                HStack(spacing: 8) {
                    Text(st.label).font(.system(size: 10, weight: .black)).foregroundStyle(Theme.statusColor(st))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Theme.statusColor(st).opacity(0.18), in: RoundedRectangle(cornerRadius: 4))
                    StarRating(value: item.ratingValue, size: 11) { r in Task { await setRating(item, r) } }
                }
                if item.hasNotes {
                    Label(item.notes ?? "", systemImage: "note.text").font(.caption2).foregroundStyle(Theme.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            VStack(spacing: 10) {
                Button { Task { await toggleFavorite(item) } } label: {
                    Image(systemName: item.isFavorite ? "heart.fill" : "heart").foregroundStyle(item.isFavorite ? Theme.red : Theme.muted)
                }
                .buttonStyle(.plain)
                statusMenu(item)
            }
        }
        .padding(12)
        .cardStyle()
        .contentShape(Rectangle())
        .onTapGesture { editing = item }
        .contextMenu { itemMenu(item) }
    }

    // MARK: Data

    /// Loads every collection item (cursor paging) plus genre/total info per manga.
    private func load() async {
        loading = true
        errorText = nil
        do {
            var all: [CollectionItem] = []
            var cursor: String? = nil
            repeat {
                let page = try await Base44Client.shared.list("CollectionItem", CollectionItem.self, sort: "-created_date", limit: 500, cursor: cursor)
                all += page.items
                cursor = (page.has_more ?? false) ? page.next_cursor : nil
            } while cursor != nil
            items = all
            lastLoadedVersion = app.dataVersion
            struct Info: Decodable { let id: String; let genre: String?; let total_volumes: Double? }
            let ids = Array(Set(all.compactMap { nonEmpty($0.manga_id) }))
            var info: [String: (genre: String, total: Int)] = [:]
            var start = 0
            while start < ids.count {
                let chunk = Array(ids[start..<min(start + 150, ids.count)])
                let page = try await Base44Client.shared.list("Manga", Info.self, query: ["id": ["$in": chunk]], limit: 150, fields: ["id", "genre", "total_volumes"])
                for m in page.items { info[m.id] = (genre: m.genre ?? "", total: Int(m.total_volumes ?? 0)) }
                start += 150
            }
            mangaInfo = info
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func patch(_ id: String, _ change: (inout CollectionItem) -> Void) {
        if let i = items.firstIndex(where: { $0.id == id }) { change(&items[i]) }
    }

    /// Mark our own change as seen so the dataVersion bump doesn't trigger a full reload.
    private func markSeen() { lastLoadedVersion = app.dataVersion + 1 }

    private func setStatus(_ item: CollectionItem, _ s: ReadStatus) async {
        patch(item.id) { $0.status = s.rawValue }
        markSeen()
        if !(await CollectionActions.setStatus(app, item, s)) { await load() }
    }

    private func setRating(_ item: CollectionItem, _ r: Int) async {
        patch(item.id) { $0.rating = Double(r) }
        markSeen()
        if !(await CollectionActions.setRating(app, item, r)) { await load() }
    }

    private func toggleFavorite(_ item: CollectionItem) async {
        patch(item.id) { $0.favorite = !item.isFavorite }
        markSeen()
        if !(await CollectionActions.toggleFavorite(app, item)) { await load() }
    }

    private func remove(_ item: CollectionItem) async {
        items.removeAll { $0.id == item.id }
        markSeen()
        if !(await CollectionActions.remove(app, item)) { await load() }
    }
}

// MARK: - Editor sheet (status, rating, favorite, chapter progress, notes)

struct CollectionItemEditor: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State var item: CollectionItem
    var onSave: (CollectionItem) -> Void

    @State private var chapterText = ""
    @State private var notes = ""
    @State private var saving = false
    @State private var confirmRemove = false

    init(item: CollectionItem, onSave: @escaping (CollectionItem) -> Void) {
        _item = State(initialValue: item)
        self.onSave = onSave
        _chapterText = State(initialValue: item.chapter > 0 ? String(item.chapter) : "")
        _notes = State(initialValue: item.notes ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 14) {
                        CoverView(info: CoverInfo(item: item), cornerRadius: 6).frame(width: 60)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.titleText).font(.headline)
                            Text("\(item.volumeLabel) · \((item.price ?? 0).euro)").font(.caption).foregroundStyle(Theme.muted)
                        }
                    }
                }
                .listRowBackground(Theme.card)

                Section("Status") {
                    Picker("Status", selection: Binding(get: { item.effectiveStatus }, set: { s in
                        item.status = s.rawValue
                        let snapshot = item
                        Task { _ = await CollectionActions.setStatus(app, snapshot, s); onSave(snapshot) }
                    })) {
                        ForEach(ReadStatus.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.menu)
                    Toggle(isOn: Binding(get: { item.isFavorite }, set: { v in
                        let before = item
                        item.favorite = v
                        let snapshot = item
                        Task { if await CollectionActions.toggleFavorite(app, before) { onSave(snapshot) } }
                    })) {
                        Label("Favorite", systemImage: "heart.fill").foregroundStyle(Theme.red)
                    }
                }
                .listRowBackground(Theme.card)

                Section("Rating") {
                    HStack {
                        StarRating(value: item.ratingValue, size: 26) { r in
                            item.rating = Double(r)
                            let snapshot = item
                            Task { if await CollectionActions.setRating(app, snapshot, r) { onSave(snapshot) } }
                        }
                        Spacer()
                        Text(item.ratingValue > 0 ? "\(item.ratingValue)/10" : "Not rated").font(.subheadline.weight(.bold)).foregroundStyle(Theme.muted)
                    }
                }
                .listRowBackground(Theme.card)

                Section {
                    HStack {
                        Text("Current chapter")
                        Spacer()
                        TextField("0", text: $chapterText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 90)
                    }
                    if let d = nonEmpty(item.last_read_date) {
                        Text("Updated \(d)").font(.caption).foregroundStyle(Theme.muted)
                    }
                } header: { Text("Reading progress") }
                .listRowBackground(Theme.card)

                Section("Notes") {
                    TextField("Add a note...", text: $notes, axis: .vertical).lineLimit(3...8)
                }
                .listRowBackground(Theme.card)

                Section {
                    Button(role: .destructive) { confirmRemove = true } label: {
                        Label("Remove from collection", systemImage: "trash")
                    }
                }
                .listRowBackground(Theme.card)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button { Task { await saveText() } } label: {
                        if saving { ProgressView() } else { Text("Save").bold() }
                    }
                    .disabled(saving)
                }
            }
            .confirmationDialog("Remove \"\(item.titleText)\"?", isPresented: $confirmRemove, titleVisibility: .visible) {
                Button("Remove", role: .destructive) {
                    Task { if await CollectionActions.remove(app, item) { dismiss() } }
                }
            }
        }
        .presentationDetents([.large])
    }

    /// Saves chapter progress and notes (the fields the web app saves on blur).
    private func saveText() async {
        saving = true; defer { saving = false }
        let ch = Int(chapterText.trimmingCharacters(in: .whitespaces)) ?? 0
        if !chapterText.isEmpty, ch != item.chapter {
            if let day = await CollectionActions.setChapter(app, item, ch) {
                item.current_chapter = Double(ch)
                item.last_read_date = day
            }
        }
        if notes != (item.notes ?? "") {
            if await CollectionActions.setNotes(app, item, notes) { item.notes = notes }
        }
        onSave(item)
        dismiss()
    }
}
