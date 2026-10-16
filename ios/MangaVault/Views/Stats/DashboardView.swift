import SwiftUI
import Charts

/// A collected card paired with its definition (dashboard "Recent Trading Cards").
struct DashboardRecentCard: Identifiable {
    let owned: UserCard
    let card: TradingCard
    var id: String { owned.id }
}

/// An unlocked achievement row (dashboard "Recent Achievements").
private struct DashboardAchievementRow: Identifiable {
    let def: AchievementDef
    let date: String
    var id: String { def.id }
}

struct ChartDatum: Identifiable, Hashable {
    let name: String
    let value: Int
    var key: String = ""
    var id: String { name }
}

enum ChartData {
    static func status(_ items: [CollectionItem]) -> [ChartDatum] {
        [ChartDatum(name: "Reading", value: items.filter { $0.effectiveStatus == .reading }.count, key: "reading"),
         ChartDatum(name: "Completed", value: items.filter { $0.effectiveStatus == .completed }.count, key: "completed"),
         ChartDatum(name: "Plan", value: items.filter { $0.effectiveStatus == .plan_to_read }.count, key: "plan_to_read"),
         ChartDatum(name: "On Hold", value: items.filter { $0.effectiveStatus == .on_hold }.count, key: "on_hold")]
    }

    /// Cumulative volumes per month (yyyy-MM).
    static func growth(_ items: [CollectionItem]) -> [ChartDatum] {
        var perMonth: [String: Int] = [:]
        for i in items { let m = String((i.created_date ?? "").prefix(7)); if !m.isEmpty { perMonth[m, default: 0] += 1 } }
        var total = 0
        var out: [ChartDatum] = []
        for k in perMonth.keys.sorted() {
            total += perMonth[k] ?? 0
            out.append(ChartDatum(name: k, value: total))
        }
        return out
    }

    static func ratings(_ items: [CollectionItem]) -> [ChartDatum] {
        var b = [0, 0, 0, 0, 0]
        for i in items {
            let r = i.ratingValue
            if r >= 1 && r <= 2 { b[0] += 1 } else if r >= 3 && r <= 4 { b[1] += 1 }
            else if r >= 5 && r <= 6 { b[2] += 1 } else if r >= 7 && r <= 8 { b[3] += 1 } else if r >= 9 && r <= 10 { b[4] += 1 }
        }
        return zip(["1-2", "3-4", "5-6", "7-8", "9-10"], b).map { ChartDatum(name: $0, value: $1) }
    }

    static func color(forStatusKey k: String) -> Color {
        Theme.statusColor(ReadStatus(rawValue: k) ?? .reading)
    }
}


struct DashboardView: View {
    @Environment(AppState.self) private var app
    @State private var items: [CollectionItem] = []
    @State private var genreMap: [String: String] = [:]
    @State private var profile: UserProfile?
    @State private var streak = 0
    @State private var level = XP.level(for: 0)
    @State private var loading = true
    @State private var layout = DashboardSection.defaults
    @State private var dailyQuests: [QuestState] = []
    @State private var claimedQuests: Set<String> = []
    @State private var bumping: String?
    @State private var showCustomize = false
    @State private var cardCount: Int?
    @State private var capsuleCount: Int?
    @State private var recentCards: [DashboardRecentCard]?
    @State private var serverAchievements: [UnlockedAchievement]?

    private var name: String { nonEmpty(profile?.display_name) ?? app.displayName }
    private var reading: [CollectionItem] {
        Array(items.filter { $0.effectiveStatus == .reading }
            .sorted { ($0.last_read_date ?? "") > ($1.last_read_date ?? "") }
            .prefix(6))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                levelHero
                EventBannerView()
                if loading && items.isEmpty {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(0..<8, id: \.self) { _ in RoundedRectangle(cornerRadius: 14).fill(Theme.card.opacity(0.5)).frame(height: 84) }
                    }
                } else {
                    statGrid
                    vaultLinks
                    ForEach(layout.filter { !$0.hidden }) { section in
                        sectionView(section.id)
                    }
                    HStack(spacing: 10) {
                        NavigationLink(value: Route.statistics) { Label("Full Statistics", systemImage: "chart.bar.xaxis").frame(maxWidth: .infinity) }
                            .buttonStyle(OutlineButtonStyle(color: Theme.primary, border: Theme.primary.opacity(0.4)))
                        NavigationLink(value: Route.rewards) { Label("Rewards", systemImage: "gift.fill").frame(maxWidth: .infinity) }
                            .buttonStyle(PrimaryButtonStyle())
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Home")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Wordmark(size: 18) }
            ToolbarItem(placement: .topBarLeading) {
                Button { showCustomize = true } label: { Image(systemName: "slider.horizontal.3") }
                    .accessibilityLabel("Customize dashboard")
            }
        }
        .sheet(isPresented: $showCustomize) {
            DashboardCustomizeSheet(layout: layout) { new in Task { await saveLayout(new) } }
                .presentationDetents([.medium, .large])
        }
        .refreshable { await load() }
        .task(id: app.dataVersion) { await load() }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("DASHBOARD", systemImage: "sparkles").font(.caption.weight(.bold)).tracking(2).foregroundStyle(Theme.primary)
            welcomeText
                .font(.system(size: 30, weight: .black)).tracking(-1)
            Text("Level \(level.level) · \(level.total) XP · \(streak)-day streak").font(.subheadline).foregroundStyle(Theme.muted)
        }
    }

    private var welcomeText: Text {
        let first: Text = Text("Welcome back, ").foregroundStyle(Theme.primary)
        let second: Text = Text(name).foregroundStyle(Theme.foreground)
        return first + second
    }

    private var levelHero: some View {
        VStack(alignment: .leading, spacing: 12) {
            NavigationLink(value: Route.achievements) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Level \(level.level)").font(.headline.weight(.black)).foregroundStyle(Theme.primary)
                        Spacer()
                        Text("\(level.into) / \(level.span) XP to level \(level.level + 1)").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                    }
                    ProgressBar(value: level.progress)
                }
            }
            .buttonStyle(.plain)
            HStack(spacing: 10) {
                Button { app.openRoute = nil; app.selectTab(.collection) } label: {
                    Label("My Collection", systemImage: "books.vertical").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                if let first = reading.first, let mid = first.manga_id {
                    NavigationLink(value: Route.volume(mangaId: mid, volume: first.volume ?? 1)) {
                        Label("Continue Reading", systemImage: "book").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle())
                } else {
                    Button { app.selectTab(.collection) } label: {
                        Label("Continue Reading", systemImage: "book").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle())
                }
            }
        }
        .padding(16)
        .cardStyle(highlighted: true)
    }

    private var statGrid: some View {
        let count: (ReadStatus) -> Int = { s in items.filter { $0.effectiveStatus == s }.count }
        let rated: [CollectionItem] = items.filter { ($0.rating ?? 0) > 0 }
        let ratingSum: Double = rated.reduce(0.0) { (sum: Double, i: CollectionItem) -> Double in sum + (i.rating ?? 0) }
        let avg: String = rated.isEmpty ? "—" : String(format: "%.1f", ratingSum / Double(rated.count))
        let totalValue: Double = items.reduce(0.0) { (sum: Double, i: CollectionItem) -> Double in sum + (i.price ?? 0) }
        let favoriteCount: Int = items.filter { $0.isFavorite }.count
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            StatCard(icon: "books.vertical", label: "Volumes", value: "\(items.count)")
            StatCard(icon: "book", label: "Reading", value: "\(count(.reading))")
            StatCard(icon: "checkmark.circle", label: "Completed", value: "\(count(.completed))", accent: Theme.emerald)
            StatCard(icon: "bookmark", label: "Plan to Read", value: "\(count(.plan_to_read))", accent: Theme.sky)
            StatCard(icon: "pause.circle", label: "On Hold", value: "\(count(.on_hold))", accent: Theme.orange)
            StatCard(icon: "heart", label: "Favorites", value: "\(favoriteCount)", accent: Theme.red)
            StatCard(icon: "eurosign.circle", label: "Total Value", value: totalValue.euro)
            StatCard(icon: "star", label: "Avg Rating", value: avg, accent: Theme.orange)
        }
    }

    /// Trading cards · achievements · sealed capsules (web: quick links row).
    private var vaultLinks: some View {
        HStack(spacing: 10) {
            vaultLink(.cards, "rectangle.stack.fill", "Trading Cards", countText(cardCount))
            vaultLink(.achievements, "trophy.fill", "Achievements", String(unlockedAchievements.count))
            vaultLink(.capsules, "shippingbox.fill", "Sealed Capsules", countText(capsuleCount))
        }
    }

    private func countText(_ n: Int?) -> String {
        guard let n else { return "…" }
        return String(n)
    }

    private func vaultLink(_ route: Route, _ icon: String, _ label: String, _ value: String) -> some View {
        NavigationLink(value: route) {
            VStack(alignment: .leading, spacing: 6) {
                Label(label, systemImage: icon).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted).lineLimit(1).minimumScaleFactor(0.7)
                Text(value).font(.title3.weight(.black)).foregroundStyle(Theme.foreground)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .cardStyle()
        }
        .buttonStyle(.plain)
    }

    // MARK: Sections (order + visibility from dashboard_layout)

    @ViewBuilder private func sectionView(_ id: String) -> some View {
        switch id {
        case "continue": continueReading
        case "quests": questsSummary
        case "achievements": recentAchievements
        case "cards": recentCardsSection
        case "recent": recentlyAdded
        case "charts": charts
        default: EmptyView()
        }
    }

    private func sectionTitle(_ title: String, _ icon: String, link: (String, Route)? = nil) -> some View {
        HStack {
            Label(title, systemImage: icon).font(.title3.weight(.black)).labelStyle(TintedIconLabelStyle())
            Spacer()
            if let link {
                NavigationLink(value: link.1) { Text(link.0).font(.caption.weight(.bold)).foregroundStyle(Theme.primary) }
            }
        }
    }

    private var continueReading: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("CONTINUE READING", "flame.fill")
            if reading.isEmpty {
                VStack(spacing: 12) {
                    Text("You're not reading anything right now. Set a volume to \"Reading\" in your collection.")
                        .font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
                    HStack {
                        Button("My Collection") { app.selectTab(.collection) }.buttonStyle(PrimaryButtonStyle())
                        Button("Browse catalog") { app.selectTab(.catalog) }.buttonStyle(OutlineButtonStyle())
                    }
                }
                .frame(maxWidth: .infinity).padding(20).cardStyle()
            } else {
                VStack(spacing: 10) {
                    ForEach(reading) { readingRow($0) }
                }
            }
        }
    }

    private func readingRow(_ i: CollectionItem) -> some View {
        HStack(spacing: 12) {
            NavigationLink(value: Route.manga(i.manga_id ?? "")) {
                CoverView(info: CoverInfo(item: i), cornerRadius: 6).frame(width: 56)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                Text(i.titleText).font(.subheadline.weight(.bold)).lineLimit(1)
                Text(readingVolumeLine(i)).font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
                Text(chapterLine(i))
                    .font(.caption2).foregroundStyle(Theme.muted)
                HStack(spacing: 8) {
                    Button { Task { await bumpChapter(i) } } label: {
                        HStack(spacing: 4) {
                            if bumping == i.id { ProgressView().controlSize(.mini).tint(.white) } else { Image(systemName: "plus") }
                            Text("1 chapter")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .foregroundStyle(.white)
                        .background(Theme.primary, in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .disabled(bumping == i.id)
                    NavigationLink(value: Route.volume(mangaId: i.manga_id ?? "", volume: i.volume ?? 1)) {
                        Text("Open").font(.caption.weight(.bold))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .cardStyle()
    }

    private func readingVolumeLine(_ i: CollectionItem) -> String {
        let vol: String = i.volume.map { String($0) } ?? "—"
        return "Vol. \(vol) · Reading"
    }

    private func chapterLine(_ i: CollectionItem) -> String {
        let chapter: String = i.chapter > 0 ? "Chapter \(i.chapter)" : "No chapter logged yet"
        guard let d = nonEmpty(i.last_read_date) else { return chapter }
        return chapter + " · " + d
    }

    private var unlockedAchievements: [UnlockedAchievement] {
        serverAchievements ?? profile?.unlocked_achievements ?? []
    }

    private var questsSummary: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("DAILY QUESTS", "target", link: ("Claim rewards", Route.quests))
            VStack(spacing: 8) {
                ForEach(dailyQuests) { q in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(q.def.name).font(.subheadline.weight(.bold))
                            Spacer()
                            Label("\(q.def.reward) XP", systemImage: "star.fill").font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
                        }
                        ProgressBar(value: Double(q.value) / Double(max(q.def.total, 1)), height: 6)
                        HStack {
                            Spacer()
                            if claimedQuests.contains(q.id) {
                                Label("Claimed", systemImage: "checkmark").font(.caption2.weight(.bold)).foregroundStyle(Theme.emerald)
                            } else if q.completed {
                                NavigationLink(value: Route.quests) { Text("Ready to claim →").font(.caption2.weight(.bold)).foregroundStyle(Theme.primary) }
                            } else {
                                Text("\(q.value)/\(q.def.total)").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                            }
                        }
                    }
                    .padding(12)
                    .cardStyle()
                }
            }
        }
    }

    private var recentAchievements: some View {
        let unlocked = unlockedAchievements.sorted { ($0.date ?? "") > ($1.date ?? "") }
        var rows: [DashboardAchievementRow] = []
        for u in unlocked where rows.count < 4 {
            if let def = AchievementDef.all.first(where: { $0.id == u.id }) {
                rows.append(DashboardAchievementRow(def: def, date: String((u.date ?? "").prefix(10))))
            }
        }
        return VStack(alignment: .leading, spacing: 12) {
            sectionTitle("RECENT ACHIEVEMENTS", "trophy.fill", link: ("View all", Route.achievements))
            if rows.isEmpty {
                Text("No achievements unlocked yet. Visit Achievements to check your progress.")
                    .font(.subheadline).foregroundStyle(Theme.muted).padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
            } else {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(rows) { r in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(r.def.rarity.label.uppercased()).font(.caption2.weight(.black)).foregroundStyle(Theme.primary)
                            Text(r.def.name).font(.subheadline.weight(.black)).lineLimit(1)
                            Text(r.def.desc).font(.caption2).foregroundStyle(Theme.muted).lineLimit(2)
                            Text("\(r.date) · +\(r.def.reward) XP").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .cardStyle()
                    }
                }
            }
        }
    }

    private var recentCardsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("RECENT TRADING CARDS", "rectangle.stack.fill", link: ("View all", Route.cards))
            if let cards = recentCards {
                if cards.isEmpty {
                    Text("No cards yet. Claim your starter pack on the Trading Cards page.")
                        .font(.subheadline).foregroundStyle(Theme.muted).padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(cards) { pair in
                                TradingCardView(card: pair.card, owned: true, count: pair.owned.count, size: .sm)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                }
            } else {
                ProgressView().tint(Theme.primary).frame(maxWidth: .infinity).padding()
            }
        }
    }

    private var recentlyAdded: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("RECENTLY ADDED", "sparkles")
            if items.isEmpty {
                Text("Your collection is empty.").font(.subheadline).foregroundStyle(Theme.muted).frame(maxWidth: .infinity).padding(24).cardStyle()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(Array(items.prefix(6))) { i in
                            NavigationLink(value: Route.manga(i.manga_id ?? "")) {
                                VStack(alignment: .leading, spacing: 4) {
                                    CoverView(info: CoverInfo(item: i))
                                    Text(i.titleText).font(.caption.weight(.bold)).lineLimit(1)
                                }
                                .frame(width: 110)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private var viewAll: AnyView {
        AnyView(NavigationLink(value: Route.statistics) { Text("View all").font(.caption.weight(.bold)).foregroundStyle(Theme.primary) })
    }

    @ViewBuilder private var charts: some View {
        let status = ChartData.status(items).filter { $0.value > 0 }
        let genres = VaultService.genreDistribution(items, genreMap, top: 8).map { ChartDatum(name: $0.0, value: $0.1) }
        VStack(spacing: 14) {
            SectionCard(title: "Status Distribution", trailing: viewAll) {
                if status.isEmpty { emptyChart } else {
                    Chart(status) { d in
                        SectorMark(angle: .value("Count", d.value), innerRadius: .ratio(0.55), angularInset: 2)
                            .foregroundStyle(ChartData.color(forStatusKey: d.key))
                            .cornerRadius(3)
                    }
                    .frame(height: 190)
                    FlowLayout(spacing: 12) {
                        ForEach(status) { d in
                            HStack(spacing: 5) {
                                Circle().fill(ChartData.color(forStatusKey: d.key)).frame(width: 9, height: 9)
                                Text("\(d.name) \(d.value)").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                            }
                        }
                    }
                }
            }
            SectionCard(title: "Top Genres", trailing: viewAll) {
                if genres.isEmpty { emptyChart } else { GenreBarChart(data: genres) }
            }
            SectionCard(title: "Collection Growth", trailing: viewAll) {
                let g = ChartData.growth(items)
                if g.isEmpty { emptyChart } else { GrowthChart(data: g) }
            }
            SectionCard(title: "Rating Distribution", trailing: viewAll) {
                Chart(ChartData.ratings(items)) { d in
                    BarMark(x: .value("Rating", d.name), y: .value("Count", d.value))
                        .foregroundStyle(Theme.primary).cornerRadius(4)
                }
                .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.border); AxisValueLabel().foregroundStyle(Theme.muted) } }
                .chartXAxis { AxisMarks { _ in AxisValueLabel().foregroundStyle(Theme.muted) } }
                .frame(height: 180)
            }
        }
    }

    private var emptyChart: some View {
        Text("No data yet.").font(.subheadline).foregroundStyle(Theme.muted).frame(maxWidth: .infinity, minHeight: 120)
    }

    // MARK: Data

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            async let itemsT = VaultService.collection()
            async let actsT = VaultService.activities()
            let its = try await itemsT
            let acts = try await actsT
            let gm = try await VaultService.genreMap(for: its)
            items = its; genreMap = gm
            streak = VaultService.streak(acts)
            if let uid = app.user?.id {
                await ProfileStore.shared.invalidate()
                profile = await ProfileStore.shared.current(userId: uid)
            }
            layout = DashboardSection.normalized(profile?.dashboard_layout)

            let today = DayKey.local()
            var dc: [String: Int] = [:]
            acts.filter { $0.day == today }.forEach { dc[$0.type ?? "", default: 0] += 1 }
            let local = QuestDef.all.map { QuestState(def: $0, value: min(dc[$0.key] ?? 0, $0.total)) }
            dailyQuests = local

            if let st = try? await Engines.rewardsState() {
                level = st.level.info
                serverAchievements = st.achievements
                let server = st.quests ?? []
                if !server.isEmpty {
                    dailyQuests = local.map { q in
                        guard let s = server.first(where: { $0.id == q.def.id }) else { return q }
                        return QuestState(def: q.def, value: min(s.value, q.def.total), serverCompleted: s.completed)
                    }
                }
                claimedQuests = Set(server.filter { $0.claimed == true }.map { $0.id })
                for f in st.fresh ?? [] {
                    app.show("Unlocked: \(f.label ?? "")", f.xp > 0 ? "+\(f.xp) XP" : "Open it in Vault Capsules")
                }
                if !(st.fresh ?? []).isEmpty { app.celebrate() }
            } else {
                let genreCount: Int = VaultService.genreCount(its, gm)
                let baseXP: Int = XP.base(items: its, genres: genreCount)
                let bonusXP: Int = profile?.xpBonus ?? 0
                level = XP.level(for: baseXP + bonusXP)
            }
            await loadVaultCounts()
        } catch {
            if !(error is CancellationError) { app.fail("Could not load dashboard", error) }
        }
    }

    private func loadVaultCounts() async {
        cardCount = (try? await Base44Client.shared.count("UserCard")) ?? 0
        if let uid = app.user?.id {
            capsuleCount = (try? await Base44Client.shared.count("VaultCapsule", query: ["user_id": uid, "opened": false])) ?? 0
        }
        let mine = (try? await Engines.myCards(limit: 6, sort: "-created_date")) ?? []
        if mine.isEmpty { recentCards = []; return }
        let defs = (try? await Engines.cards(ids: Array(Set(mine.map { $0.card_id })))) ?? []
        recentCards = mine.compactMap { u -> DashboardRecentCard? in
            guard let def = defs.first(where: { $0.card_id == u.card_id }) else { return nil }
            return DashboardRecentCard(owned: u, card: def)
        }
    }

    /// "+1 chapter" quick action (web: Continue Reading card).
    private func bumpChapter(_ i: CollectionItem) async {
        bumping = i.id
        defer { bumping = nil }
        let next = i.chapter + 1
        let today = DayKey.local()
        do {
            try await Base44Client.shared.update("CollectionItem", i.id, ["current_chapter": next, "last_read_date": today])
            if let idx = items.firstIndex(where: { $0.id == i.id }) {
                items[idx].current_chapter = Double(next)
                items[idx].last_read_date = today
            }
            VaultService.log("progress", mangaId: i.manga_id, title: i.title, volume: i.volume, meta: "ch.\(next)")
            dailyQuests = dailyQuests.map { $0.def.key == "progress" ? QuestState(def: $0.def, value: $0.def.total) : $0 }
            app.show("Progress saved: chapter \(next)")
        } catch { app.fail("Could not save progress", error) }
    }

    private func saveLayout(_ new: [DashboardSection]) async {
        layout = new
        guard let p = profile else { return }
        do {
            let sections: [[String: Any]] = new.map { s -> [String: Any] in ["id": s.id, "hidden": s.hidden] }
            let body: [String: Any] = ["dashboard_layout": sections]
            try await Base44Client.shared.update("UserProfile", p.id, body)
            await ProfileStore.shared.invalidate()
        } catch { app.fail("Could not save layout", error) }
    }
}

/// Reorder / hide dashboard sections (web: "Customize Dashboard").
struct DashboardCustomizeSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var layout: [DashboardSection]
    var onChange: ([DashboardSection]) -> Void

    init(layout: [DashboardSection], onChange: @escaping ([DashboardSection]) -> Void) {
        _layout = State(initialValue: layout)
        self.onChange = onChange
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(layout.indices, id: \.self) { idx in
                        let s = layout[idx]
                        HStack {
                            Text(s.label).foregroundStyle(s.hidden ? Theme.muted : Theme.foreground)
                            Spacer()
                            Button { move(idx, -1) } label: { Image(systemName: "arrow.up") }.buttonStyle(.borderless).disabled(idx == 0)
                            Button { move(idx, 1) } label: { Image(systemName: "arrow.down") }.buttonStyle(.borderless).disabled(idx == layout.count - 1)
                            Button { toggle(idx) } label: { Image(systemName: s.hidden ? "eye.slash" : "eye") }.buttonStyle(.borderless)
                        }
                        .listRowBackground(Theme.card)
                    }
                }
                Section {
                    Button { layout = DashboardSection.defaults; onChange(layout) } label: {
                        Label("Restore default", systemImage: "arrow.counterclockwise")
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Customize Dashboard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func move(_ i: Int, _ d: Int) {
        let j = i + d
        guard j >= 0, j < layout.count else { return }
        layout.swapAt(i, j)
        onChange(layout)
    }

    private func toggle(_ i: Int) {
        layout[i].hidden.toggle()
        onChange(layout)
    }
}


struct GenreBarChart: View {
    let data: [ChartDatum]
    var body: some View {
        Chart(data) { d in
            BarMark(x: .value("Count", d.value), y: .value("Genre", d.name.capitalized))
                .foregroundStyle(Theme.primary).cornerRadius(4)
        }
        .chartXAxis(.hidden)
        .chartYAxis { AxisMarks { _ in AxisValueLabel().foregroundStyle(Theme.muted) } }
        .frame(height: CGFloat(max(120, data.count * 30)))
    }
}

struct GrowthChart: View {
    let data: [ChartDatum]
    var body: some View {
        Chart(data) { d in
            AreaMark(x: .value("Month", d.name), y: .value("Total", d.value))
                .foregroundStyle(LinearGradient(colors: [Theme.primary.opacity(0.5), Theme.primary.opacity(0)], startPoint: .top, endPoint: .bottom))
                .interpolationMethod(.monotone)
            LineMark(x: .value("Month", d.name), y: .value("Total", d.value))
                .foregroundStyle(Theme.primary).lineStyle(StrokeStyle(lineWidth: 2))
                .interpolationMethod(.monotone)
        }
        .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.border); AxisValueLabel().foregroundStyle(Theme.muted) } }
        .chartXAxis { AxisMarks { _ in AxisValueLabel().foregroundStyle(Theme.muted) } }
        .frame(height: 190)
    }
}
