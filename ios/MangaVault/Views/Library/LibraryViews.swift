import SwiftUI

// Library & discovery screens: Calendar, Recommendations, Journal, Random, Compare,
// Series Tracker, Tier Lists and Roulette (ports of the matching web app pages).

// MARK: - Private helpers

fileprivate struct LVMessage: Error {
    let text: String
}

fileprivate func lvCancelled(_ error: Error) -> Bool {
    if error is CancellationError { return true }
    if let u = error as? URLError, u.code == .cancelled { return true }
    return false
}

fileprivate func lvTrim(_ s: String) -> String {
    s.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Int from a text field, or NSNull for the payload.
fileprivate func lvNumberOrNull(_ s: String) -> Any {
    if let n = Int(lvTrim(s)) { return n }
    return NSNull()
}

fileprivate func lvRegex(_ s: String) -> [String: Any] {
    ["$regex": NSRegularExpression.escapedPattern(for: s), "$options": "i"]
}

fileprivate enum LVDates {
    static let day: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    static let monthTitle: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.timeZone = .current
        f.dateFormat = "LLLL yyyy"
        return f
    }()
    static let monthShort: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.timeZone = .current
        f.dateFormat = "LLL"
        return f
    }()

    static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = .current
        c.firstWeekday = 2
        return c
    }
}

fileprivate struct LVSmallButtonStyle: ButtonStyle {
    var color: Color = Theme.foreground
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.caption.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(Theme.secondary.opacity(configuration.isPressed ? 1 : 0.6), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
    }
}

fileprivate func lvFieldLabel(_ text: String) -> some View {
    Text(text.uppercased()).font(.caption2.weight(.black)).tracking(1).foregroundStyle(Theme.muted)
}

// MARK: - 1. Reading Calendar

struct CalendarView: View {
    @Environment(AppState.self) private var app
    @State private var activities: [ReadingActivity] = []
    @State private var sessions: [ReadingSession] = []
    @State private var loading = true
    @State private var month = Date()
    @State private var showForm = false
    @State private var fTitle = ""
    @State private var fDate = Date()
    @State private var fVol = ""
    @State private var fMin = ""
    @State private var fCh = ""
    @State private var fNotes = ""
    @State private var saving = false

    private static let weekdayNames = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    private var dayCounts: [String: Int] {
        var m: [String: Int] = [:]
        for a in activities {
            let d = a.day
            if !d.isEmpty { m[d, default: 0] += 1 }
        }
        for s in sessions {
            let d = s.day
            if !d.isEmpty { m[d, default: 0] += 2 }
        }
        return m
    }

    private var sessionDays: Set<String> {
        Set(sessions.map { $0.day }.filter { !$0.isEmpty })
    }

    private var activeDays: [String] { Array(dayCounts.keys) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Reading Calendar", eyebrowIcon: "calendar", lead: "READING", accent: "CALENDAR",
                             subtitle: "Track every reading day, build streaks, and log sessions from your real activity.")
                if loading && activities.isEmpty && sessions.isEmpty {
                    LoadingView()
                } else {
                    statsGrid
                    monthCard
                    yearCard
                    sessionsCard
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Reading Calendar")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    // MARK: Stats

    private var statsGrid: some View {
        let days = activeDays
        let set = Set(days)
        let streak = DayKey.streak(days)
        let longest = DayKey.longest(days)
        let week = weekActiveCount(set)
        let monthKey = String(DayKey.local().prefix(7))
        let monthCount = days.filter { $0.hasPrefix(monthKey) }.count
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            StatCard(icon: "flame.fill", label: "Current Streak", value: "\(streak)d")
            StatCard(icon: "trophy.fill", label: "Longest Streak", value: "\(longest)d", accent: Theme.amber)
            StatCard(icon: "calendar.badge.checkmark", label: "Active This Week", value: "\(week)/7", accent: Theme.emerald)
            StatCard(icon: "calendar", label: "Active This Month", value: "\(monthCount)", accent: Theme.sky)
        }
    }

    private func weekActiveCount(_ set: Set<String>) -> Int {
        let start = DayKey.weekStart()
        let cal = LVDates.calendar
        var n = 0
        for i in 0..<7 {
            if let d = cal.date(byAdding: .day, value: i, to: start), set.contains(DayKey.local(d)) { n += 1 }
        }
        return n
    }

    // MARK: Month grid

    private var monthStart: Date {
        let cal = LVDates.calendar
        return cal.date(from: cal.dateComponents([.year, .month], from: month)) ?? month
    }

    private var monthCells: [String?] {
        let cal = LVDates.calendar
        let start = monthStart
        let weekday = cal.component(.weekday, from: start) // 1 = Sunday
        let lead = (weekday + 5) % 7
        let count = cal.range(of: .day, in: .month, for: start)?.count ?? 30
        var cells: [String?] = Array(repeating: nil, count: lead)
        for i in 0..<count {
            if let d = cal.date(byAdding: .day, value: i, to: start) { cells.append(DayKey.local(d)) }
        }
        while cells.count % 7 != 0 { cells.append(nil) }
        return cells
    }

    private func shiftMonth(_ delta: Int) {
        month = LVDates.calendar.date(byAdding: .month, value: delta, to: monthStart) ?? month
    }

    private var monthCard: some View {
        let counts = dayCounts
        let sDays = sessionDays
        let today = DayKey.local()
        let cells = monthCells
        let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Text(LVDates.monthTitle.string(from: monthStart))
                    .font(.headline.weight(.black))
                    .foregroundStyle(Theme.foreground)
                Spacer()
                Button { shiftMonth(-1) } label: { Image(systemName: "chevron.left") }
                    .buttonStyle(LVSmallButtonStyle())
                Button("Today") { month = Date() }
                    .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
                Button { shiftMonth(1) } label: { Image(systemName: "chevron.right") }
                    .buttonStyle(LVSmallButtonStyle())
            }
            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(Self.weekdayNames, id: \.self) { name in
                    Text(name).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                }
                ForEach(0..<cells.count, id: \.self) { i in
                    dayCell(cells[i], counts: counts, sessionDays: sDays, today: today)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func cellFill(_ count: Int) -> Color {
        if count > 0 {
            let level: Double = 0.35 + Double(count) * 0.18
            return Theme.primary.opacity(min(1.0, level))
        }
        return Theme.secondary.opacity(0.5)
    }

    @ViewBuilder
    private func dayCell(_ key: String?, counts: [String: Int], sessionDays: Set<String>, today: String) -> some View {
        if let key {
            let c = counts[key] ?? 0
            let num = Int(key.suffix(2)) ?? 0
            let isToday = key == today
            let hasSession = sessionDays.contains(key)
            ZStack {
                RoundedRectangle(cornerRadius: 7).fill(cellFill(c))
                RoundedRectangle(cornerRadius: 7).stroke(isToday ? Theme.primary : Color.clear, lineWidth: 2)
                Text("\(num)")
                    .font(.caption.weight(c > 0 ? .black : .medium))
                    .foregroundStyle(c > 0 ? Color.white : Theme.muted)
                if hasSession {
                    VStack {
                        Spacer()
                        Circle().fill(Color.white).frame(width: 4, height: 4).padding(.bottom, 4)
                    }
                }
            }
            .frame(height: 38)
        } else {
            Color.clear.frame(height: 38)
        }
    }

    // MARK: Year activity

    private var yearBuckets: [(label: String, count: Int)] {
        let cal = LVDates.calendar
        let thisMonth = cal.date(from: cal.dateComponents([.year, .month], from: Date())) ?? Date()
        let days = activeDays
        var out: [(label: String, count: Int)] = []
        for i in stride(from: 11, through: 0, by: -1) {
            guard let d = cal.date(byAdding: .month, value: -i, to: thisMonth) else { continue }
            let key = String(DayKey.local(d).prefix(7))
            let n = days.filter { $0.hasPrefix(key) }.count
            out.append((label: LVDates.monthShort.string(from: d), count: n))
        }
        return out
    }

    private var yearCard: some View {
        let buckets = yearBuckets
        let maxCount = max(1, buckets.map { $0.count }.max() ?? 1)
        return SectionCard(title: "Year Activity (last 12 months)") {
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(0..<buckets.count, id: \.self) { i in
                    yearBar(label: buckets[i].label, count: buckets[i].count, maxCount: maxCount)
                }
            }
        }
    }

    private func yearBar(label: String, count: Int, maxCount: Int) -> some View {
        let ratio: Double = Double(count) / Double(maxCount)
        let scaled: Double = 0.2 + 0.8 * ratio
        let opacity: Double = count > 0 ? scaled : 0.08
        return VStack(spacing: 4) {
            Text("\(count)").font(.system(size: 9, weight: .bold)).foregroundStyle(Theme.muted)
            RoundedRectangle(cornerRadius: 4)
                .fill(Theme.primary.opacity(opacity))
                .frame(height: 44)
            Text(String(label.prefix(1))).font(.system(size: 9, weight: .bold)).foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Sessions

    private var sessionsCard: some View {
        SectionCard(title: "Reading Sessions", trailing: AnyView(logButton)) {
            if showForm { sessionForm }
            sessionList
        }
    }

    private var logButton: some View {
        Button {
            withAnimation { showForm.toggle() }
        } label: {
            Label(showForm ? "Cancel" : "Log Session", systemImage: showForm ? "xmark" : "plus")
        }
        .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
    }

    private var sessionForm: some View {
        VStack(alignment: .leading, spacing: 10) {
            lvFieldLabel("Manga Title")
            TextField("e.g. One Piece", text: $fTitle).textFieldStyle(VaultFieldStyle())
            DatePicker("Date", selection: $fDate, displayedComponents: .date)
                .tint(Theme.primary)
            HStack(spacing: 8) {
                TextField("Vol.", text: $fVol).keyboardType(.numberPad).textFieldStyle(VaultFieldStyle())
                TextField("Min.", text: $fMin).keyboardType(.numberPad).textFieldStyle(VaultFieldStyle())
                TextField("Ch.", text: $fCh).keyboardType(.numberPad).textFieldStyle(VaultFieldStyle())
            }
            TextField("Notes (private)", text: $fNotes, axis: .vertical)
                .lineLimit(2...5)
                .textFieldStyle(VaultFieldStyle())
            Button {
                Task { await saveSession() }
            } label: {
                HStack {
                    if saving { ProgressView().tint(.white) }
                    Text("Save Session")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(saving)
        }
        .padding(12)
        .background(Theme.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var sessionList: some View {
        if sessions.isEmpty {
            Text("No manual sessions logged yet. Your reading activity (progress updates, status changes) still counts toward streaks automatically.")
                .font(.subheadline)
                .foregroundStyle(Theme.muted)
        } else {
            VStack(spacing: 8) {
                ForEach(Array(sessions.prefix(30))) { s in
                    sessionRow(s)
                }
            }
        }
    }

    private func sessionSubtitle(_ s: ReadingSession) -> String {
        var parts: [String] = [s.day]
        if let v = s.volume_number { parts.append("Vol. \(Int(v))") }
        let mins = Int(s.minutes ?? 0)
        if mins > 0 { parts.append("\(mins) min") }
        let ch = Int(s.chapters_read ?? 0)
        if ch > 0 { parts.append("\(ch) ch") }
        return parts.joined(separator: " · ")
    }

    private func sessionRow(_ s: ReadingSession) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "book.fill").foregroundStyle(Theme.primary).padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(nonEmpty(s.title) ?? "Reading session")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.foreground)
                Text(sessionSubtitle(s)).font(.caption).foregroundStyle(Theme.muted)
                if let n = nonEmpty(s.notes) {
                    Text(n).font(.caption).foregroundStyle(Theme.foreground.opacity(0.8))
                }
            }
            Spacer(minLength: 0)
            Button {
                Task { await deleteSession(s) }
            } label: {
                Image(systemName: "trash").foregroundStyle(Theme.red)
            }
            .buttonStyle(.plain)
        }
        .padding(10)
        .background(Theme.secondary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Data

    private func load() async {
        loading = true
        do {
            let acts = try await VaultService.activities(limit: 2000)
            let page = try await Base44Client.shared.list("ReadingSession", ReadingSession.self, sort: "-date", limit: 1000)
            activities = acts
            sessions = page.items
        } catch {
            if !lvCancelled(error) { app.fail("Could not load calendar", error) }
        }
        loading = false
    }

    private func saveSession() async {
        let title = lvTrim(fTitle)
        let date = DayKey.local(fDate)
        guard !title.isEmpty, !date.isEmpty else {
            app.show("Title and date are required", error: true)
            return
        }
        saving = true
        defer { saving = false }
        let payload: [String: Any] = [
            "manga_id": "",
            "title": title,
            "volume_number": lvNumberOrNull(fVol),
            "date": date,
            "minutes": Int(lvTrim(fMin)) ?? 0,
            "chapters_read": Int(lvTrim(fCh)) ?? 0,
            "notes": lvTrim(fNotes)
        ]
        do {
            _ = try await Base44Client.shared.create("ReadingSession", payload, ReadingSession.self)
            app.show("Reading session logged")
            fTitle = ""; fVol = ""; fMin = ""; fCh = ""; fNotes = ""; fDate = Date()
            showForm = false
            await load()
        } catch {
            app.fail("Could not log session", error)
        }
    }

    private func deleteSession(_ s: ReadingSession) async {
        do {
            try await Base44Client.shared.delete("ReadingSession", s.id)
            sessions.removeAll { $0.id == s.id }
            app.show("Session deleted")
        } catch {
            app.fail("Could not delete session", error)
        }
    }
}

// MARK: - 2. Smart Recommendations

fileprivate struct LVRecommendation: Identifiable {
    let manga: Manga
    let reason: String
    var id: String { manga.id }
}

struct RecommendationsView: View {
    @Environment(AppState.self) private var app
    @State private var mode = "foryou"
    @State private var recs: [LVRecommendation] = []
    @State private var newGenre: String?
    @State private var newRecs: [Manga] = []
    @State private var loading = true
    @State private var hasCollection = false
    @State private var dismissed: Set<String> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Recommendations", eyebrowIcon: "wand.and.stars", lead: "SMART", accent: "RECOMMENDATIONS",
                             subtitle: "Based on your favorite genres and the series you already own — no fabricated ratings or data.")
                HStack(spacing: 8) {
                    Chip(title: "Recommended for you", icon: "sparkles", selected: mode == "foryou") { mode = "foryou" }
                    Chip(title: "Discover a new genre", icon: "safari", selected: mode == "new") { mode = "new" }
                }
                if loading {
                    LoadingView()
                } else if mode == "foryou" {
                    forYouContent
                } else {
                    newGenreContent
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Recommendations")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    @ViewBuilder
    private var forYouContent: some View {
        if recs.isEmpty {
            if hasCollection {
                EmptyState(icon: "sparkles", text: "No more recommendations right now. Try refreshing.",
                           buttonTitle: "Refresh", action: { Task { await load() } })
            } else {
                EmptyState(icon: "books.vertical", text: "Add a few manga to your collection so we can learn your taste.")
            }
        } else {
            LazyVGrid(columns: gridColumns, spacing: 20) {
                ForEach(recs) { r in
                    recCard(r.manga, reason: r.reason)
                }
            }
        }
    }

    @ViewBuilder
    private var newGenreContent: some View {
        if let g = newGenre {
            VStack(alignment: .leading, spacing: 4) {
                Text("Try a new genre: \(g.capitalized)")
                    .font(.title3.weight(.black))
                    .foregroundStyle(Theme.foreground)
                Text("You don't own any manga in this genre yet.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
            LazyVGrid(columns: gridColumns, spacing: 20) {
                ForEach(newRecs) { m in
                    recCard(m, reason: "New genre: \(g.capitalized)")
                }
            }
        } else {
            EmptyState(icon: "safari", text: "No new genres to suggest right now — you're already exploring widely!")
        }
    }

    private func recCard(_ m: Manga, reason: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            NavigationLink(value: Route.manga(m.id)) {
                CoverView(info: CoverInfo(manga: m))
            }
            .buttonStyle(.plain)
            Text(m.title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.foreground)
                .lineLimit(2)
            Label(reason, systemImage: "sparkles")
                .font(.caption)
                .foregroundStyle(Theme.primary)
                .lineLimit(2)
            HStack(spacing: 8) {
                NavigationLink(value: Route.manga(m.id)) {
                    Text("View").frame(maxWidth: .infinity)
                }
                .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
                Button {
                    Task { await dismiss(m) }
                } label: {
                    Image(systemName: "hand.thumbsdown")
                }
                .buttonStyle(LVSmallButtonStyle(color: Theme.muted))
                .accessibilityLabel("Not interested")
            }
        }
    }

    // MARK: Data

    private static func reason(for m: Manga, top: [String]) -> String {
        let g = (m.genre ?? "").lowercased()
        for t in top where g.contains(t) {
            return "Because you enjoy \(t)"
        }
        return "Popular in the vault"
    }

    private func load() async {
        guard let user = app.user else { loading = false; return }
        loading = true
        defer { loading = false }
        do {
            let profile = try await ProfileStore.shared.ensure(userId: user.id)
            let dis = Set(profile.dismissed_recommendations ?? [])
            dismissed = dis
            let items = try await VaultService.collection(limit: 2000)
            hasCollection = !items.isEmpty
            let gmap = try await VaultService.genreMap(for: items)
            let owned = Set(items.compactMap { nonEmpty($0.manga_id) })
            let top: [String] = VaultService.genreDistribution(items, gmap, top: 5).map { $0.0 }

            var picked: [Manga] = []
            var seen = Set<String>()
            if !top.isEmpty {
                let pattern = top.map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "|")
                let q: [String: Any] = ["genre": ["$regex": pattern, "$options": "i"]]
                let page = try await Base44Client.shared.list("Manga", Manga.self, query: q, sort: "title", limit: 500, fields: Manga.listFields)
                for m in page.items where !owned.contains(m.id) && !dis.contains(m.id) && !seen.contains(m.id) {
                    picked.append(m); seen.insert(m.id)
                }
            }
            if picked.count < 6 {
                let page = try await Base44Client.shared.list("Manga", Manga.self, sort: "-created_date", limit: 200, fields: Manga.listFields)
                for m in page.items where !owned.contains(m.id) && !dis.contains(m.id) && !seen.contains(m.id) {
                    picked.append(m); seen.insert(m.id)
                }
            }
            recs = picked.prefix(24).map { LVRecommendation(manga: $0, reason: Self.reason(for: $0, top: top)) }

            await loadNewGenre(top: top, owned: owned, dismissed: dis)
        } catch {
            if !lvCancelled(error) { app.fail("Could not load recommendations", error) }
        }
    }

    private func loadNewGenre(top: [String], owned: Set<String>, dismissed dis: Set<String>) async {
        guard let all = try? await Base44Client.shared.distinct("Manga", field: "genre") else {
            newGenre = nil; newRecs = []
            return
        }
        var set = Set<String>()
        for g in all {
            for part in g.split(separator: ",") {
                let t = part.trimmingCharacters(in: .whitespaces).lowercased()
                if !t.isEmpty { set.insert(t) }
            }
        }
        let candidates = set.subtracting(top)
        guard let g = candidates.randomElement() else {
            newGenre = nil; newRecs = []
            return
        }
        let q: [String: Any] = ["genre": lvRegex(g)]
        let page = try? await Base44Client.shared.list("Manga", Manga.self, query: q, limit: 6, fields: Manga.listFields)
        let list = page?.items ?? []
        newGenre = g
        newRecs = list.filter { !owned.contains($0.id) && !dis.contains($0.id) }
    }

    private func dismiss(_ m: Manga) async {
        recs.removeAll { $0.manga.id == m.id }
        newRecs.removeAll { $0.id == m.id }
        dismissed.insert(m.id)
        guard let user = app.user else { return }
        do {
            let profile = try await ProfileStore.shared.ensure(userId: user.id)
            let merged = Array(Set(profile.dismissed_recommendations ?? []).union(dismissed))
            try await Base44Client.shared.update("UserProfile", profile.id, ["dismissed_recommendations": merged])
            await ProfileStore.shared.invalidate()
        } catch {
            app.fail("Could not save preference", error)
        }
    }
}

// MARK: - 3. Manga Journal

fileprivate struct LVJournalDraft: Identifiable {
    let id = UUID()
    var entryId: String?
    var mangaId: String
    var title: String
    var volume: String
    var type: JournalType
    var content: String
    var rating: Int
    var spoiler: Bool

    init(entry: JournalEntry?) {
        entryId = entry?.id
        mangaId = entry?.manga_id ?? ""
        title = entry?.title ?? ""
        volume = entry?.volume_number.map { String(Int($0)) } ?? ""
        type = entry?.type ?? .note
        content = entry?.content ?? ""
        rating = Int(entry?.rating ?? 0)
        spoiler = entry?.is_spoiler ?? false
    }
}

struct JournalView: View {
    @Environment(AppState.self) private var app
    @State private var entries: [JournalEntry] = []
    @State private var loading = true
    @State private var search = ""
    @State private var sort = "recent"
    @State private var typeFilter: JournalType?
    @State private var draft: LVJournalDraft?
    @State private var revealed: Set<String> = []

    private var filtered: [JournalEntry] {
        let q = lvTrim(search).lowercased()
        var r = entries.filter { e in
            q.isEmpty || (e.content ?? "").lowercased().contains(q) || (e.title ?? "").lowercased().contains(q)
        }
        if let typeFilter { r = r.filter { $0.type == typeFilter } }
        switch sort {
        case "title":
            r.sort { ($0.title ?? "").localizedCaseInsensitiveCompare($1.title ?? "") == .orderedAscending }
        case "type":
            r.sort { a, b in
                let ta = a.entry_type ?? "", tb = b.entry_type ?? ""
                if ta != tb { return ta < tb }
                return (a.created_date ?? "") > (b.created_date ?? "")
            }
        default:
            r.sort { ($0.created_date ?? "") > ($1.created_date ?? "") }
        }
        return r
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Manga Journal", eyebrowIcon: "note.text", lead: "MY MANGA", accent: "JOURNAL",
                             subtitle: "Private notes, favorite moments, ratings and reminders — for series and volumes.")
                Button {
                    draft = LVJournalDraft(entry: nil)
                } label: {
                    Label("New Entry", systemImage: "plus").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                controls
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Manga Journal")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $draft) { d in
            LVJournalEditor(draft: d) {
                Task { await load() }
            }
        }
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                TextField("Search entries...", text: $search).textFieldStyle(VaultFieldStyle())
                Menu {
                    Picker("Sort", selection: $sort) {
                        Text("Most Recent").tag("recent")
                        Text("Manga A-Z").tag("title")
                        Text("By Type").tag("type")
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .frame(width: 44, height: 44)
                        .foregroundStyle(Theme.foreground)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Chip(title: "All", selected: typeFilter == nil) { typeFilter = nil }
                    ForEach(JournalType.allCases) { t in
                        Chip(title: t.label, icon: t.icon, selected: typeFilter == t) { typeFilter = t }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if loading && entries.isEmpty {
            LoadingView()
        } else if filtered.isEmpty {
            EmptyState(icon: "note.text", text: entries.isEmpty ? "Your journal is empty." : "No entries match your filters.")
        } else {
            LazyVStack(spacing: 12) {
                ForEach(filtered) { e in
                    entryCard(e)
                }
            }
        }
    }

    private func metaLine(_ e: JournalEntry) -> String {
        var parts: [String] = []
        if let t = nonEmpty(e.title) { parts.append(t) }
        if let v = e.volume_number { parts.append("Vol. \(Int(v))") }
        return parts.joined(separator: " · ")
    }

    private func entryRoute(_ e: JournalEntry) -> Route? {
        guard let id = nonEmpty(e.manga_id) else { return nil }
        if let v = e.volume_number { return .volume(mangaId: id, volume: Int(v)) }
        return .manga(id)
    }

    private func entryCard(_ e: JournalEntry) -> some View {
        let rating = Int(e.rating ?? 0)
        let isSpoiler = e.is_spoiler ?? false
        let hidden = isSpoiler && !revealed.contains(e.id)
        let meta = metaLine(e)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Badge(text: e.type.label.uppercased(), icon: e.type.icon, fg: Theme.primary, bg: Theme.primary.opacity(0.15))
                if isSpoiler {
                    Badge(text: "SPOILER", icon: "eye.slash", fg: Theme.red, bg: Theme.red.opacity(0.15))
                }
                Spacer()
                Text(String((e.created_date ?? "").prefix(10))).font(.caption2).foregroundStyle(Theme.muted)
            }
            if !meta.isEmpty {
                Text(meta).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
            }
            if rating > 0 {
                HStack(spacing: 6) {
                    StarRating(value: rating, size: 11)
                    Text("\(rating)/10").font(.caption.weight(.bold)).foregroundStyle(Theme.orange)
                }
            }
            Text(e.content ?? "")
                .font(.subheadline)
                .foregroundStyle(Theme.foreground.opacity(0.9))
                .blur(radius: hidden ? 6 : 0)
                .contentShape(Rectangle())
                .onTapGesture {
                    if isSpoiler {
                        if revealed.contains(e.id) { revealed.remove(e.id) } else { revealed.insert(e.id) }
                    }
                }
            if hidden {
                Text("Tap to reveal spoiler").font(.caption2).foregroundStyle(Theme.muted)
            }
            entryActions(e)
        }
        .padding(14)
        .cardStyle()
    }

    private func entryActions(_ e: JournalEntry) -> some View {
        HStack(spacing: 8) {
            if let route = entryRoute(e) {
                NavigationLink(value: route) {
                    Label("Open manga", systemImage: "book")
                }
                .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
            }
            Spacer()
            Button {
                draft = LVJournalDraft(entry: e)
            } label: {
                Label("Edit", systemImage: "pencil")
            }
            .buttonStyle(LVSmallButtonStyle())
            Button {
                Task { await delete(e) }
            } label: {
                Label("Delete", systemImage: "trash")
            }
            .buttonStyle(LVSmallButtonStyle(color: Theme.red))
        }
    }

    private func load() async {
        loading = true
        do {
            let page = try await Base44Client.shared.list("JournalEntry", JournalEntry.self, sort: "-created_date", limit: 1000)
            entries = page.items
        } catch {
            if !lvCancelled(error) { app.fail("Could not load journal", error) }
        }
        loading = false
    }

    private func delete(_ e: JournalEntry) async {
        do {
            try await Base44Client.shared.delete("JournalEntry", e.id)
            entries.removeAll { $0.id == e.id }
            app.show("Entry deleted")
        } catch {
            app.fail("Could not delete entry", error)
        }
    }
}

fileprivate struct LVJournalEditor: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var draft: LVJournalDraft
    @State private var saving = false
    private let onSaved: () -> Void

    init(draft: LVJournalDraft, onSaved: @escaping () -> Void) {
        _draft = State(initialValue: draft)
        self.onSaved = onSaved
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Manga") {
                    TextField("Manga Title", text: $draft.title)
                    TextField("Volume (optional)", text: $draft.volume)
                        .keyboardType(.numberPad)
                }
                .listRowBackground(Theme.card)

                Section("Type") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(JournalType.allCases) { t in
                                Chip(title: t.label, icon: t.icon, selected: draft.type == t) { draft.type = t }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                .listRowBackground(Theme.card)

                Section("Content") {
                    TextField("Write your thoughts...", text: $draft.content, axis: .vertical)
                        .lineLimit(4...12)
                }
                .listRowBackground(Theme.card)

                Section("Rating") {
                    HStack {
                        StarRating(value: draft.rating, size: 24) { r in
                            draft.rating = (draft.rating == r) ? 0 : r
                        }
                        Spacer()
                        Text(draft.rating > 0 ? "\(draft.rating)/10" : "Not rated")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Theme.muted)
                    }
                }
                .listRowBackground(Theme.card)

                Section {
                    Toggle("Contains spoilers", isOn: $draft.spoiler).tint(Theme.primary)
                }
                .listRowBackground(Theme.card)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle(draft.entryId == nil ? "New Entry" : "Edit Entry")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await save() }
                    } label: {
                        if saving { ProgressView() } else { Text("Save").bold() }
                    }
                    .disabled(saving)
                }
            }
        }
    }

    private func save() async {
        let content = lvTrim(draft.content)
        guard !content.isEmpty else {
            app.show("Content is required", error: true)
            return
        }
        saving = true
        defer { saving = false }
        let payload: [String: Any] = [
            "manga_id": draft.mangaId,
            "title": lvTrim(draft.title),
            "volume_number": lvNumberOrNull(draft.volume),
            "entry_type": draft.type.rawValue,
            "content": content,
            "rating": draft.rating,
            "is_spoiler": draft.spoiler
        ]
        do {
            if let id = draft.entryId {
                try await Base44Client.shared.update("JournalEntry", id, payload)
                app.show("Entry updated")
            } else {
                _ = try await Base44Client.shared.create("JournalEntry", payload, JournalEntry.self)
                app.show("Journal entry saved")
            }
            onSaved()
            dismiss()
        } catch {
            app.fail("Could not save entry", error)
        }
    }
}

// MARK: - 4. Random Manga

fileprivate enum LVRandomSource: String, CaseIterable {
    case wishlist, unread, genre, notowned, favorites

    var label: String {
        switch self {
        case .wishlist: return "From your wishlist"
        case .unread: return "Unread from your collection"
        case .genre: return "From a genre"
        case .notowned: return "Manga you do not own"
        case .favorites: return "From your favorites"
        }
    }

    var icon: String {
        switch self {
        case .wishlist: return "heart"
        case .unread: return "book.closed"
        case .genre: return "tag"
        case .notowned: return "safari"
        case .favorites: return "star"
        }
    }
}

fileprivate struct LVPick {
    let title: String
    let cover: CoverInfo
    let mangaId: String
    let volume: Int?
    let genre: String?
    let owned: Bool
}

struct RandomMangaView: View {
    @Environment(AppState.self) private var app
    @State private var source: LVRandomSource = .unread
    @State private var genres: [String] = []
    @State private var genre: String?
    @State private var rolling = false
    @State private var result: LVPick?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Random Discovery", eyebrowIcon: "dice.fill", lead: "RANDOM", accent: "MANGA",
                             subtitle: "Can't decide what to read next? Let the vault pick for you.")
                sourcePicker
                if source == .genre { genrePicker }
                Button {
                    Task { await roll() }
                } label: {
                    HStack {
                        Image(systemName: "dice.fill")
                        Text(rolling ? "Rolling..." : "Roll the dice")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(rolling)
                resultArea
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Random Manga")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadGenres() }
    }

    private var sourcePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            lvFieldLabel("Source")
            FlowLayout(spacing: 8) {
                ForEach(LVRandomSource.allCases, id: \.self) { s in
                    Chip(title: s.label, icon: s.icon, selected: source == s) { source = s }
                }
            }
        }
    }

    private var genrePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            lvFieldLabel("Genre")
            if genres.isEmpty {
                Text("Loading genres...").font(.caption).foregroundStyle(Theme.muted)
            } else {
                FlowLayout(spacing: 8) {
                    ForEach(Array(genres.prefix(20)), id: \.self) { g in
                        Chip(title: g, selected: (genre ?? genres.first) == g) { genre = g }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var resultArea: some View {
        if rolling {
            VStack(spacing: 12) {
                ProgressView().tint(Theme.primary).controlSize(.large)
                Text("Rolling...").font(.subheadline.weight(.bold)).foregroundStyle(Theme.muted)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 50)
        } else if let r = result {
            resultCard(r)
        } else {
            EmptyState(icon: "dice", text: "Choose a source and roll the dice to discover your next read.")
        }
    }

    private func route(_ r: LVPick) -> Route {
        if let v = r.volume { return .volume(mangaId: r.mangaId, volume: v) }
        return .manga(r.mangaId)
    }

    private func resultCard(_ r: LVPick) -> some View {
        VStack(spacing: 12) {
            CoverView(info: r.cover)
                .frame(width: 170)
                .overlay(alignment: .topLeading) {
                    Badge(text: "YOUR PICK", icon: "sparkles").padding(8)
                }
                .shadow(color: Theme.primary.opacity(0.35), radius: 16)
            Text(r.owned ? "From your collection" : "Not in your collection")
                .font(.caption.weight(.bold))
                .foregroundStyle(r.owned ? Theme.emerald : Theme.sky)
            Text(r.title)
                .font(.title3.weight(.black))
                .foregroundStyle(Theme.foreground)
                .multilineTextAlignment(.center)
            if let v = r.volume {
                Text("Volume \(v)").font(.subheadline).foregroundStyle(Theme.muted)
            }
            if let g = nonEmpty(r.genre) {
                Text(g).font(.caption).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            }
            HStack(spacing: 10) {
                NavigationLink(value: route(r)) {
                    Text("Open details").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(r.mangaId.isEmpty)
                Button {
                    Task { await roll() }
                } label: {
                    Label("Reroll", systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle())
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .cardStyle(highlighted: true)
    }

    // MARK: Data

    private func loadGenres() async {
        guard genres.isEmpty else { return }
        guard let all = try? await Base44Client.shared.distinct("Manga", field: "genre") else { return }
        var set = Set<String>()
        for g in all {
            for part in g.split(separator: ",") {
                let t = part.trimmingCharacters(in: .whitespaces)
                if !t.isEmpty { set.insert(t) }
            }
        }
        genres = set.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private func roll() async {
        guard !rolling else { return }
        rolling = true
        result = nil
        do {
            let pick = try await pickOne()
            try? await Task.sleep(for: .milliseconds(600))
            result = pick
        } catch let m as LVMessage {
            app.show(m.text, error: true)
        } catch {
            if !lvCancelled(error) { app.fail("Could not roll", error) }
        }
        rolling = false
    }

    private func genreOf(_ mangaId: String?) async -> String? {
        guard let id = nonEmpty(mangaId) else { return nil }
        let m = try? await Base44Client.shared.get("Manga", id, Manga.self)
        return nonEmpty(m?.genre)
    }

    private func shelfPick(_ it: CollectionItem) async -> LVPick {
        let g = await genreOf(it.manga_id)
        let vol: Int? = it.isOmnibus ? nil : it.volume
        return LVPick(title: it.titleText, cover: CoverInfo(item: it), mangaId: it.manga_id ?? "",
                      volume: vol, genre: g, owned: true)
    }

    private func mangaPick(_ m: Manga, owned: Bool) -> LVPick {
        LVPick(title: m.title, cover: CoverInfo(manga: m), mangaId: m.id, volume: nil, genre: m.genre, owned: owned)
    }

    private func pickOne() async throws -> LVPick {
        let api = Base44Client.shared
        switch source {
        case .wishlist:
            let items = try await api.list("WishlistItem", WishlistItem.self, limit: 500).items
            guard let w = items.randomElement() else { throw LVMessage(text: "Your wishlist is empty") }
            let g = await genreOf(w.manga_id)
            let vol: Int? = w.isOmnibus ? nil : w.volume
            return LVPick(title: w.title ?? "", cover: CoverInfo(wish: w), mangaId: w.manga_id ?? "",
                          volume: vol, genre: g, owned: false)
        case .unread:
            let items = try await api.list("CollectionItem", CollectionItem.self, limit: 1000).items
            let unread = items.filter { $0.effectiveStatus != .completed }
            guard let it = unread.randomElement() else { throw LVMessage(text: "No unread volumes in your collection") }
            return await shelfPick(it)
        case .favorites:
            let q: [String: Any] = ["favorite": true]
            let items = try await api.list("CollectionItem", CollectionItem.self, query: q, limit: 500).items
            guard let it = items.randomElement() else { throw LVMessage(text: "No favorites marked yet") }
            return await shelfPick(it)
        case .notowned:
            let ownedList = try await api.distinct("CollectionItem", field: "manga_id")
            let owned = Set(ownedList)
            let page = try await api.list("Manga", Manga.self, sort: "-created_date", limit: 500, fields: Manga.listFields)
            let candidates = page.items.filter { !owned.contains($0.id) }
            guard let m = candidates.randomElement() else { throw LVMessage(text: "You own everything shown here!") }
            return mangaPick(m, owned: false)
        case .genre:
            let g = genre ?? genres.first ?? ""
            guard !g.isEmpty else { throw LVMessage(text: "No manga found for \(g)") }
            let q: [String: Any] = ["genre": lvRegex(g)]
            let page = try await api.list("Manga", Manga.self, query: q, limit: 200, fields: Manga.listFields)
            guard let m = page.items.randomElement() else { throw LVMessage(text: "No manga found for \(g)") }
            let ownedList = (try? await api.distinct("CollectionItem", field: "manga_id")) ?? []
            return mangaPick(m, owned: ownedList.contains(m.id))
        }
    }
}

// MARK: - 5. Manga Comparison

fileprivate struct LVCompareStats {
    let owned: Int
    let read: Int
    let favorite: Bool
    let avgRating: Double?
    let wishlist: Int
}

struct CompareView: View {
    @Environment(AppState.self) private var app
    @State private var pickA: Manga?
    @State private var pickB: Manga?
    @State private var statsA: LVCompareStats?
    @State private var statsB: LVCompareStats?
    @State private var loadingStats = false

    private var pairKey: String { "\(pickA?.id ?? "")|\(pickB?.id ?? "")" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Compare", eyebrowIcon: "arrow.left.arrow.right", lead: "MANGA", accent: "COMPARISON",
                             subtitle: "Pick two series to compare side by side. Public metadata on top, your personal stats below.")
                LVComparePicker(label: "Manga A", selection: $pickA)
                LVComparePicker(label: "Manga B", selection: $pickB)
                comparison
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Compare")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: pairKey) { await loadStats() }
    }

    @ViewBuilder
    private var comparison: some View {
        if let a = pickA, let b = pickB {
            publicTable(a, b)
            personalTable(a, b)
            HStack(spacing: 10) {
                NavigationLink(value: Route.manga(a.id)) {
                    Text("Open A").frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle(color: Theme.primary))
                NavigationLink(value: Route.manga(b.id)) {
                    Text("Open B").frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle(color: Theme.primary))
            }
        } else {
            EmptyState(icon: "arrow.left.arrow.right", text: "Search and select two manga to compare them.")
        }
    }

    private func headerRow(_ a: Manga, _ b: Manga) -> some View {
        HStack(spacing: 8) {
            Text("").frame(width: 100, alignment: .leading)
            Text("A").font(.caption.weight(.black)).foregroundStyle(Theme.primary).frame(maxWidth: .infinity, alignment: .leading)
            Text("B").font(.caption.weight(.black)).foregroundStyle(Theme.primary).frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func row(_ label: String, _ a: String, _ b: String) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 8) {
                Text(label)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.muted)
                    .frame(width: 100, alignment: .leading)
                Text(a)
                    .font(.caption)
                    .foregroundStyle(Theme.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(b)
                    .font(.caption)
                    .foregroundStyle(Theme.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 8)
            Divider().overlay(Theme.border)
        }
    }

    private func volumesText(_ m: Manga) -> String {
        m.totalVolumes > 0 ? "\(m.totalVolumes)" : "Unknown"
    }

    private func languagesText(_ m: Manga) -> String {
        let l = m.languages ?? []
        return l.isEmpty ? "—" : l.joined(separator: ", ")
    }

    private func publicTable(_ a: Manga, _ b: Manga) -> some View {
        SectionCard(title: "Public metadata") {
            VStack(spacing: 0) {
                headerRow(a, b)
                row("Title", a.title, b.title)
                row("Publisher", nonEmpty(a.publisher) ?? "—", nonEmpty(b.publisher) ?? "—")
                row("Genres", nonEmpty(a.genre) ?? "—", nonEmpty(b.genre) ?? "—")
                row("Known Volumes", volumesText(a), volumesText(b))
                row("Edition", a.isOmnibus ? "Omnibus" : "Standard", b.isOmnibus ? "Omnibus" : "Standard")
                row("Price", a.priceValue.euro, b.priceValue.euro)
                row("Languages", languagesText(a), languagesText(b))
            }
        }
    }

    private func statText(_ s: LVCompareStats?, _ dash: String, _ f: (LVCompareStats) -> String) -> String {
        guard let s else { return dash }
        return f(s)
    }

    private func favoriteText(_ s: LVCompareStats) -> String {
        s.favorite ? "Yes" : "No"
    }

    private func avgText(_ s: LVCompareStats?) -> String {
        guard let s else { return "…" }
        if let avg = s.avgRating { return String(format: "%.1f/10", avg) }
        return "Not rated"
    }

    private func personalTable(_ a: Manga, _ b: Manga) -> some View {
        let sa = statsA, sb = statsB
        let dash = loadingStats ? "…" : "—"
        return SectionCard(title: "Your personal stats") {
            VStack(spacing: 0) {
                headerRow(a, b)
                row("Owned Volumes", statText(sa, dash) { String($0.owned) }, statText(sb, dash) { String($0.owned) })
                row("Read Volumes", statText(sa, dash) { String($0.read) }, statText(sb, dash) { String($0.read) })
                row("On Wishlist", statText(sa, dash) { String($0.wishlist) }, statText(sb, dash) { String($0.wishlist) })
                row("Your Avg Rating", avgText(sa), avgText(sb))
                row("Favorite", statText(sa, dash, favoriteText), statText(sb, dash, favoriteText))
            }
        }
    }

    private func stats(for id: String) async throws -> LVCompareStats {
        let q: [String: Any] = ["manga_id": id]
        let items = try await Base44Client.shared.list("CollectionItem", CollectionItem.self, query: q, limit: 500).items
        let wish = try await Base44Client.shared.list("WishlistItem", WishlistItem.self, query: q, limit: 500).items
        let owned = items.filter { !$0.isOmnibus }.count
        let read = items.filter { $0.effectiveStatus == .completed }.count
        let fav = items.contains { $0.isFavorite }
        let rated = items.compactMap { $0.rating }.filter { $0 > 0 }
        var avg: Double? = nil
        if !rated.isEmpty {
            let sum: Double = rated.reduce(0.0, +)
            let raw: Double = sum / Double(rated.count)
            let tenths: Double = (raw * 10).rounded()
            avg = tenths / 10
        }
        return LVCompareStats(owned: owned, read: read, favorite: fav, avgRating: avg, wishlist: wish.count)
    }

    private func loadStats() async {
        statsA = nil
        statsB = nil
        guard let a = pickA, let b = pickB else { return }
        loadingStats = true
        do {
            let sa = try await stats(for: a.id)
            let sb = try await stats(for: b.id)
            statsA = sa
            statsB = sb
        } catch {
            if !lvCancelled(error) { app.fail("Could not load your stats", error) }
        }
        loadingStats = false
    }
}

fileprivate struct LVComparePicker: View {
    let label: String
    @Binding var selection: Manga?
    @State private var query = ""
    @State private var results: [Manga] = []
    @State private var searching = false

    init(label: String, selection: Binding<Manga?>) {
        self.label = label
        self._selection = selection
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            lvFieldLabel(label)
            if let m = selection {
                selectedView(m)
            } else {
                searchView
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
        .task(id: query) { await runSearch() }
    }

    private func selectedView(_ m: Manga) -> some View {
        HStack(spacing: 12) {
            CoverView(info: CoverInfo(manga: m), cornerRadius: 6).frame(width: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text(m.title).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground).lineLimit(2)
                Text(nonEmpty(m.genre) ?? "—").font(.caption).foregroundStyle(Theme.muted).lineLimit(2)
            }
            Spacer(minLength: 0)
            Button("Clear") {
                selection = nil
                query = ""
                results = []
            }
            .buttonStyle(LVSmallButtonStyle(color: Theme.red))
        }
    }

    private var searchView: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                TextField("Search manga...", text: $query)
                    .textFieldStyle(VaultFieldStyle())
                    .autocorrectionDisabled()
                if searching { ProgressView().tint(Theme.primary) }
            }
            ForEach(results) { m in
                Button {
                    selection = m
                    results = []
                } label: {
                    resultRow(m)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func resultRow(_ m: Manga) -> some View {
        HStack(spacing: 10) {
            CoverView(info: CoverInfo(manga: m), cornerRadius: 4).frame(width: 30)
            VStack(alignment: .leading, spacing: 2) {
                Text(m.title).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.foreground).lineLimit(1)
                Text(nonEmpty(m.publisher) ?? "").font(.caption2).foregroundStyle(Theme.muted).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(6)
        .contentShape(Rectangle())
        .background(Theme.secondary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8))
    }

    private func runSearch() async {
        let q = lvTrim(query)
        guard !q.isEmpty else { results = []; return }
        try? await Task.sleep(for: .milliseconds(250))
        if Task.isCancelled { return }
        searching = true
        let rq: [String: Any] = ["title": lvRegex(q)]
        let fields = Manga.listFields + ["description", "languages"]
        let page = try? await Base44Client.shared.list("Manga", Manga.self, query: rq, sort: "title", limit: 12, fields: fields)
        if !Task.isCancelled { results = page?.items ?? [] }
        searching = false
    }
}

// MARK: - 6. Series Tracker

fileprivate struct LVSeries: Identifiable {
    let id: String
    let title: String
    let genre: String
    let cover: CoverInfo
    let owned: Int
    let read: Int
    let total: Int

    var pct: Double? {
        guard total > 0 else { return nil }
        let ratio: Double = Double(owned) / Double(total)
        return min(100.0, ratio * 100.0)
    }
    var readPct: Double {
        guard owned > 0 else { return 0 }
        let ratio: Double = Double(read) / Double(owned)
        return min(100.0, ratio * 100.0)
    }
    var allRead: Bool { owned > 0 && read == owned }
    var unread: Int { max(0, owned - read) }
}

struct SeriesTrackerView: View {
    @Environment(AppState.self) private var app
    @State private var series: [LVSeries] = []
    @State private var loading = true
    @State private var search = ""
    @State private var filter = "all"

    private var filtered: [LVSeries] {
        let q = lvTrim(search).lowercased()
        return series.filter { s in
            if !q.isEmpty && !s.title.lowercased().contains(q) { return false }
            switch filter {
            case "reading": return s.read > 0
            case "incomplete": return !s.allRead
            case "complete": return s.allRead
            default: return true
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Series Tracker", eyebrowIcon: "books.vertical.fill", lead: "SERIES", accent: "COMPLETION",
                             subtitle: "Track your progress through every series you own, volume by volume.")
                statsGrid
                TextField("Search series...", text: $search).textFieldStyle(VaultFieldStyle())
                filterChips
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Series Tracker")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task(id: app.dataVersion) { await load() }
    }

    private var statsGrid: some View {
        let owned = series.reduce(0) { $0 + $1.owned }
        let read = series.reduce(0) { $0 + $1.read }
        let complete = series.filter { $0.allRead }.count
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            StatCard(icon: "books.vertical", label: "Series", value: "\(series.count)")
            StatCard(icon: "book.closed", label: "Owned Volumes", value: "\(owned)", accent: Theme.sky)
            StatCard(icon: "checkmark.circle", label: "Read Volumes", value: "\(read)", accent: Theme.emerald)
            StatCard(icon: "trophy", label: "Completed Series", value: "\(complete)", accent: Theme.amber)
        }
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Chip(title: "All", selected: filter == "all") { filter = "all" }
                Chip(title: "Reading", selected: filter == "reading") { filter = "reading" }
                Chip(title: "Incomplete", selected: filter == "incomplete") { filter = "incomplete" }
                Chip(title: "Complete", selected: filter == "complete") { filter = "complete" }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if loading && series.isEmpty {
            LoadingView()
        } else if filtered.isEmpty {
            EmptyState(icon: "books.vertical",
                       text: series.isEmpty ? "Add manga to your collection to start tracking series." : "No series match your filters.")
        } else {
            LazyVStack(spacing: 12) {
                ForEach(filtered) { s in
                    seriesRow(s)
                }
            }
        }
    }

    private func seriesRow(_ s: LVSeries) -> some View {
        HStack(alignment: .top, spacing: 12) {
            NavigationLink(value: Route.manga(s.id)) {
                CoverView(info: s.cover, cornerRadius: 6).frame(width: 64)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 6) {
                seriesHeader(s)
                seriesCounts(s)
                seriesBars(s)
            }
        }
        .padding(12)
        .cardStyle(highlighted: s.allRead)
    }

    private func seriesHeader(_ s: LVSeries) -> some View {
        HStack(alignment: .top, spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                NavigationLink(value: Route.manga(s.id)) {
                    Text(s.title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.foreground)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                .buttonStyle(.plain)
                if !s.genre.isEmpty {
                    Text(s.genre).font(.caption2).foregroundStyle(Theme.muted).lineLimit(1)
                }
                if s.allRead {
                    Badge(text: "SERIES COMPLETE", icon: "checkmark.seal.fill", bg: Theme.emerald)
                }
            }
            Spacer(minLength: 0)
            NavigationLink(value: Route.manga(s.id)) {
                Text("Open")
            }
            .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
        }
    }

    private func seriesCounts(_ s: LVSeries) -> some View {
        let totalText = s.total > 0 ? "\(s.total)" : "?"
        return HStack(spacing: 10) {
            Text("Owned: \(s.owned) / \(totalText)")
            Text("Read: \(s.read)").foregroundStyle(Theme.emerald)
            Text("Unread: \(s.unread)")
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(Theme.muted)
    }

    private func seriesBars(_ s: LVSeries) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            if let pct = s.pct {
                Text(verbatim: "Collection \(Int(pct.rounded()))%").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                ProgressBar(value: pct / 100, height: 6)
            } else {
                Text("Owned volumes").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                ProgressBar(value: 1, height: 6, color: Theme.sky)
            }
            Text(verbatim: "Read \(Int(s.readPct.rounded()))%").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
            ProgressBar(value: s.readPct / 100, height: 6, color: Theme.emerald)
        }
    }

    private func load() async {
        loading = true
        do {
            let items = try await VaultService.collection(limit: 2000)
            let ids = Array(Set(items.compactMap { nonEmpty($0.manga_id) }))
            var mangas: [String: Manga] = [:]
            let fields = ["id", "title", "total_volumes", "genre", "cover_image_url", "cover_seed", "is_shonen_jump"]
            var start = 0
            while start < ids.count {
                let chunk = Array(ids[start..<min(start + 500, ids.count)])
                let q: [String: Any] = ["id": ["$in": chunk]]
                let page = try await Base44Client.shared.list("Manga", Manga.self, query: q, limit: 500, fields: fields)
                for m in page.items { mangas[m.id] = m }
                start += 500
            }
            var groups: [String: [CollectionItem]] = [:]
            for it in items {
                guard let id = nonEmpty(it.manga_id) else { continue }
                groups[id, default: []].append(it)
            }
            var out: [LVSeries] = []
            for (id, list) in groups {
                guard let first = list.first else { continue }
                let m = mangas[id]
                let owned = list.filter { !$0.isOmnibus }.count
                let read = list.filter { $0.effectiveStatus == .completed }.count
                let cover: CoverInfo = m.map { CoverInfo(manga: $0) } ?? CoverInfo(item: first)
                out.append(LVSeries(id: id,
                                    title: m?.title ?? first.titleText,
                                    genre: m?.genre ?? "",
                                    cover: cover,
                                    owned: owned,
                                    read: read,
                                    total: m?.totalVolumes ?? 0))
            }
            out.sort { ($0.owned + $0.read) > ($1.owned + $1.read) }
            series = out
        } catch {
            if !lvCancelled(error) { app.fail("Could not load series", error) }
        }
        loading = false
    }
}

// MARK: - 7. Tier Lists

fileprivate struct LVTierItem: Identifiable {
    let id: String
    var entry: TierEntry

    init(entry: TierEntry) {
        self.id = UUID().uuidString
        self.entry = entry
    }
}

fileprivate struct LVTierDraft: Identifiable {
    let id = UUID()
    var recordId: String
    var name: String
    var description: String
    var listType: String
    var tiers: [TierDef]
    var entries: [LVTierItem]
    var isPublic: Bool

    init(list: TierList) {
        recordId = list.id
        name = list.name ?? "My Tier List"
        description = list.description ?? ""
        listType = list.list_type ?? "manga"
        let t = list.tiers ?? []
        let defs = t.isEmpty ? TierList.defaultTiers : t
        tiers = defs
        entries = (list.entries ?? []).map { e in
            var fixed = e
            if fixed.tier < -1 || fixed.tier >= defs.count { fixed.tier = -1 }
            return LVTierItem(entry: fixed)
        }
        isPublic = list.is_public ?? false
    }

    init(listType: String) {
        recordId = ""
        name = "My Tier List"
        description = ""
        self.listType = listType
        tiers = TierList.defaultTiers
        entries = []
        isPublic = false
    }
}

struct TierListsView: View {
    @Environment(AppState.self) private var app
    @State private var lists: [TierList] = []
    @State private var loading = true
    @State private var editing: LVTierDraft?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Tier Lists", eyebrowIcon: "list.number", lead: "MANGA & CHARACTER", accent: "TIER LISTS",
                             subtitle: "Rank series and characters into tiers. Drag and drop, save, and share.")
                HStack(spacing: 10) {
                    Button {
                        editing = LVTierDraft(listType: "manga")
                    } label: {
                        Label("New Manga List", systemImage: "books.vertical.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    Button {
                        editing = LVTierDraft(listType: "character")
                    } label: {
                        Label("New Character List", systemImage: "person.2.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle(color: Theme.primary))
                }
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Tier Lists")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .fullScreenCover(item: $editing) { d in
            LVTierEditor(draft: d) {
                Task { await load() }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if loading && lists.isEmpty {
            LoadingView()
        } else if lists.isEmpty {
            EmptyState(icon: "list.number", text: "No tier lists yet. Create your first one above.")
        } else {
            LazyVStack(spacing: 12) {
                ForEach(lists) { t in
                    listCard(t)
                }
            }
        }
    }

    private func tierCount(_ def: TierDef, count: Int) -> some View {
        HStack(spacing: 4) {
            Text(def.name)
                .font(.caption2.weight(.black))
                .foregroundStyle(.white)
                .frame(width: 20, height: 20)
                .background(Color(hex: def.color), in: RoundedRectangle(cornerRadius: 4))
            Text("\(count)").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
        }
    }

    private func listCard(_ t: TierList) -> some View {
        let raw = t.tiers ?? []
        let tiers = raw.isEmpty ? TierList.defaultTiers : raw
        let entries = t.entries ?? []
        let isPublic = t.is_public ?? false
        let isCharacter = t.list_type == "character"
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: isCharacter ? "person.2.fill" : "books.vertical.fill").foregroundStyle(Theme.primary)
                Text(nonEmpty(t.name) ?? "Untitled").font(.headline).foregroundStyle(Theme.foreground).lineLimit(1)
                Spacer()
                Badge(text: isPublic ? "PUBLIC" : "PRIVATE", icon: isPublic ? "globe" : "lock",
                      fg: isPublic ? Theme.emerald : Theme.muted,
                      bg: isPublic ? Theme.emerald.opacity(0.15) : Theme.secondary)
            }
            if let d = nonEmpty(t.description) {
                Text(d).font(.caption).foregroundStyle(Theme.muted).lineLimit(3)
            }
            HStack(spacing: 10) {
                ForEach(0..<tiers.count, id: \.self) { i in
                    tierCount(tiers[i], count: entries.filter { $0.tier == i }.count)
                }
            }
            HStack(spacing: 8) {
                Button {
                    editing = LVTierDraft(list: t)
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                .buttonStyle(LVSmallButtonStyle(color: Theme.primary))
                Button {
                    Task { await delete(t) }
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                .buttonStyle(LVSmallButtonStyle(color: Theme.red))
            }
        }
        .padding(14)
        .cardStyle()
    }

    private func load() async {
        loading = true
        do {
            let page = try await Base44Client.shared.list("TierList", TierList.self, sort: "-created_date", limit: 200)
            lists = page.items
        } catch {
            if !lvCancelled(error) { app.fail("Could not load tier lists", error) }
        }
        loading = false
    }

    private func delete(_ t: TierList) async {
        do {
            try await Base44Client.shared.delete("TierList", t.id)
            lists.removeAll { $0.id == t.id }
            app.show("Tier list deleted")
        } catch {
            app.fail("Could not delete tier list", error)
        }
    }
}

fileprivate struct LVTierEditor: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var draft: LVTierDraft
    @State private var query = ""
    @State private var results: [Manga] = []
    @State private var characterName = ""
    @State private var saving = false
    private let onSaved: () -> Void

    init(draft: LVTierDraft, onSaved: @escaping () -> Void) {
        _draft = State(initialValue: draft)
        self.onSaved = onSaved
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    detailsCard
                    addCard
                    tiersCard
                }
                .padding(16)
            }
            .background(Theme.background)
            .navigationTitle(draft.recordId.isEmpty ? "New Tier List" : "Edit Tier List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await save() }
                    } label: {
                        if saving { ProgressView() } else { Text("Save").bold() }
                    }
                    .disabled(saving)
                }
            }
            .task(id: query) { await runSearch() }
        }
    }

    // MARK: Sections

    private var detailsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            lvFieldLabel("Title")
            TextField("My Tier List", text: $draft.name).textFieldStyle(VaultFieldStyle())
            lvFieldLabel("Description")
            TextField("What is this list about?", text: $draft.description, axis: .vertical)
                .lineLimit(2...4)
                .textFieldStyle(VaultFieldStyle())
            Toggle(isOn: $draft.isPublic) {
                Label(draft.isPublic ? "Public" : "Private", systemImage: draft.isPublic ? "globe" : "lock")
            }
            .tint(Theme.primary)
        }
        .padding(14)
        .cardStyle()
    }

    @ViewBuilder
    private var addCard: some View {
        if draft.listType == "character" {
            SectionCard(title: "Add entries") {
                HStack(spacing: 8) {
                    TextField("Character name", text: $characterName)
                        .textFieldStyle(VaultFieldStyle())
                        .onSubmit { addCharacter() }
                    Button {
                        addCharacter()
                    } label: {
                        Image(systemName: "plus")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
            }
        } else {
            SectionCard(title: "Add entries") {
                VStack(alignment: .leading, spacing: 6) {
                    TextField("Search manga to add...", text: $query)
                        .textFieldStyle(VaultFieldStyle())
                        .autocorrectionDisabled()
                    ForEach(results) { m in
                        Button {
                            addManga(m)
                        } label: {
                            searchRow(m)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func searchRow(_ m: Manga) -> some View {
        HStack(spacing: 10) {
            CoverView(info: CoverInfo(manga: m), cornerRadius: 4).frame(width: 30)
            Text(m.title).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.foreground).lineLimit(1)
            Spacer(minLength: 0)
            Image(systemName: "plus.circle.fill").foregroundStyle(Theme.primary)
        }
        .padding(6)
        .contentShape(Rectangle())
        .background(Theme.secondary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8))
    }

    private var tiersCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(0..<draft.tiers.count, id: \.self) { i in
                tierRow(index: i, def: draft.tiers[i])
            }
            poolRow
        }
    }

    private func items(in tier: Int) -> [LVTierItem] {
        draft.entries.filter { $0.entry.tier == tier }.sorted { $0.entry.order < $1.entry.order }
    }

    private func tierRow(index: Int, def: TierDef) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Text(def.name)
                .font(.title2.weight(.black))
                .foregroundStyle(.white)
                .frame(width: 46)
                .frame(minHeight: 120)
                .background(Color(hex: def.color), in: RoundedRectangle(cornerRadius: 8))
            strip(items(in: index), emptyText: "Drop entries here")
        }
        .padding(6)
        .cardStyle()
        .dropDestination(for: String.self) { ids, _ in
            guard let uid = ids.first else { return false }
            move(uid, to: index)
            return true
        }
    }

    private var poolRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Unranked pool — drag into a tier above")
                .font(.caption.weight(.bold))
                .foregroundStyle(Theme.muted)
            strip(items(in: -1), emptyText: draft.listType == "character" ? "Add characters above" : "Search and add manga above")
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
        .dropDestination(for: String.self) { ids, _ in
            guard let uid = ids.first else { return false }
            move(uid, to: -1)
            return true
        }
    }

    @ViewBuilder
    private func strip(_ list: [LVTierItem], emptyText: String) -> some View {
        if list.isEmpty {
            Text(emptyText)
                .font(.caption)
                .foregroundStyle(Theme.muted.opacity(0.7))
                .frame(maxWidth: .infinity, minHeight: 100)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(list) { item in
                        tile(item)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private func tile(_ item: LVTierItem) -> some View {
        VStack(spacing: 4) {
            tileImage(item.entry)
                .draggable(item.id)
                .overlay(alignment: .topTrailing) {
                    Button {
                        remove(item.id)
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 9, weight: .black))
                            .foregroundStyle(.white)
                            .frame(width: 20, height: 20)
                            .background(Color.black.opacity(0.75), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .padding(3)
                }
            Menu {
                moveMenu(item)
            } label: {
                HStack(spacing: 2) {
                    Text(item.entry.label).lineLimit(1)
                    Image(systemName: "chevron.down")
                }
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Theme.foreground)
                .frame(width: 64)
            }
        }
        .frame(width: 64)
    }

    @ViewBuilder
    private func tileImage(_ e: TierEntry) -> some View {
        if e.kind == "manga", let url = nonEmpty(e.cover_url) {
            RemoteImage(url: url)
                .frame(width: 64, height: 92)
                .clipShape(RoundedRectangle(cornerRadius: 6))
        } else {
            ZStack {
                RoundedRectangle(cornerRadius: 6).fill(Theme.secondary)
                RoundedRectangle(cornerRadius: 6).stroke(Theme.border)
                Text(e.label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.foreground)
                    .multilineTextAlignment(.center)
                    .lineLimit(4)
                    .padding(4)
            }
            .frame(width: 64, height: 92)
        }
    }

    @ViewBuilder
    private func moveMenu(_ item: LVTierItem) -> some View {
        ForEach(0..<draft.tiers.count, id: \.self) { i in
            Button("Move to \(draft.tiers[i].name)") { move(item.id, to: i) }
        }
        Button("Move to unranked pool") { move(item.id, to: -1) }
        Divider()
        Button {
            shift(item.id, by: -1)
        } label: {
            Label("Move left", systemImage: "arrow.left")
        }
        Button {
            shift(item.id, by: 1)
        } label: {
            Label("Move right", systemImage: "arrow.right")
        }
        Button(role: .destructive) {
            remove(item.id)
        } label: {
            Label("Remove", systemImage: "trash")
        }
    }

    // MARK: Mutations

    private static func renumber(_ list: inout [LVTierItem], tier: Int) {
        let idxs = list.indices
            .filter { list[$0].entry.tier == tier }
            .sorted { list[$0].entry.order < list[$1].entry.order }
        for (n, idx) in idxs.enumerated() {
            list[idx].entry.order = n
        }
    }

    private func move(_ uid: String, to tier: Int) {
        var list = draft.entries
        guard let i = list.firstIndex(where: { $0.id == uid }) else { return }
        let old = list[i].entry.tier
        if old == tier { return }
        list[i].entry.tier = tier
        list[i].entry.order = 1_000_000
        Self.renumber(&list, tier: tier)
        Self.renumber(&list, tier: old)
        draft.entries = list
    }

    private func shift(_ uid: String, by delta: Int) {
        var list = draft.entries
        guard let i = list.firstIndex(where: { $0.id == uid }) else { return }
        let tier = list[i].entry.tier
        Self.renumber(&list, tier: tier)
        let ordered = list.indices
            .filter { list[$0].entry.tier == tier }
            .sorted { list[$0].entry.order < list[$1].entry.order }
        guard let pos = ordered.firstIndex(of: i) else { return }
        let target = pos + delta
        guard target >= 0, target < ordered.count else { return }
        let j = ordered[target]
        let a = list[i].entry.order
        list[i].entry.order = list[j].entry.order
        list[j].entry.order = a
        draft.entries = list
    }

    private func remove(_ uid: String) {
        var list = draft.entries
        guard let i = list.firstIndex(where: { $0.id == uid }) else { return }
        let tier = list[i].entry.tier
        list.remove(at: i)
        Self.renumber(&list, tier: tier)
        draft.entries = list
    }

    private func addManga(_ m: Manga) {
        if draft.entries.contains(where: { $0.entry.ref_id == m.id }) {
            app.show("Already in this list")
            return
        }
        let e = TierEntry(kind: "manga", ref_id: m.id, label: m.title, cover_url: m.cover_image_url ?? "",
                          tier: -1, order: draft.entries.count)
        draft.entries.append(LVTierItem(entry: e))
        query = ""
        results = []
    }

    private func addCharacter() {
        let name = lvTrim(characterName)
        guard !name.isEmpty else { return }
        let e = TierEntry(kind: "character", ref_id: nil, label: name, cover_url: nil,
                          tier: -1, order: draft.entries.count)
        draft.entries.append(LVTierItem(entry: e))
        characterName = ""
    }

    private func runSearch() async {
        let q = lvTrim(query)
        guard !q.isEmpty, draft.listType != "character" else { results = []; return }
        try? await Task.sleep(for: .milliseconds(250))
        if Task.isCancelled { return }
        let rq: [String: Any] = ["title": lvRegex(q)]
        let fields = ["id", "title", "cover_image_url", "cover_seed", "total_volumes", "publisher"]
        let page = try? await Base44Client.shared.list("Manga", Manga.self, query: rq, limit: 10, fields: fields)
        if !Task.isCancelled { results = page?.items ?? [] }
    }

    private func save() async {
        let name = lvTrim(draft.name)
        saving = true
        defer { saving = false }
        let tiers: [[String: Any]] = draft.tiers.map { ["name": $0.name, "color": $0.color] }
        let entries: [[String: Any]] = draft.entries.map { $0.entry.json }
        let payload: [String: Any] = [
            "name": name.isEmpty ? "My Tier List" : name,
            "description": draft.description,
            "list_type": draft.listType,
            "tiers": tiers,
            "entries": entries,
            "is_public": draft.isPublic
        ]
        do {
            if draft.recordId.isEmpty {
                _ = try await Base44Client.shared.create("TierList", payload, TierList.self)
            } else {
                try await Base44Client.shared.update("TierList", draft.recordId, payload)
            }
            app.show("Tier list saved")
            onSaved()
            dismiss()
        } catch {
            app.fail("Could not save tier list", error)
        }
    }
}

// MARK: - 8. Manga Roulette

fileprivate struct LVWedge: Shape {
    let start: Double
    let end: Double

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: rect.midX, y: rect.midY)
        let r = min(rect.width, rect.height) / 2
        p.move(to: c)
        p.addArc(center: c, radius: r, startAngle: .degrees(start - 90), endAngle: .degrees(end - 90), clockwise: false)
        p.closeSubpath()
        return p
    }
}

fileprivate struct LVPointer: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

fileprivate struct LVWheel: View {
    let titles: [String]
    let rotation: Double

    static let colors = ["#ff7a00", "#1b1b1b", "#ff5100", "#2a2a2a", "#ff9a3d", "#161616"]

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            ZStack {
                wheel(size: size)
                    .rotationEffect(.degrees(rotation))
                hub
            }
            .frame(width: size, height: size)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .aspectRatio(1, contentMode: .fit)
        .overlay(alignment: .top) {
            LVPointer()
                .fill(Color.white)
                .frame(width: 24, height: 28)
                .shadow(color: .black.opacity(0.5), radius: 3)
                .offset(y: -8)
        }
    }

    private var hub: some View {
        ZStack {
            Circle().fill(Theme.background)
            Circle().stroke(Theme.primary, lineWidth: 3)
            Text("MV").font(.system(size: 16, weight: .black)).foregroundStyle(Theme.primary)
        }
        .frame(width: 54, height: 54)
    }

    private func wheel(size: CGFloat) -> some View {
        let n = max(titles.count, 1)
        let seg = 360.0 / Double(n)
        let r = size / 2
        return ZStack {
            ForEach(0..<titles.count, id: \.self) { i in
                LVWedge(start: Double(i) * seg, end: Double(i + 1) * seg)
                    .fill(Color(hex: Self.colors[i % Self.colors.count]))
            }
            if titles.count <= 40 {
                ForEach(0..<titles.count, id: \.self) { i in
                    label(i, seg: seg, r: r)
                }
            }
            Circle().stroke(Theme.primary, lineWidth: 4)
        }
        .frame(width: size, height: size)
    }

    private static func short(_ s: String) -> String {
        s.count > 16 ? String(s.prefix(15)) + "…" : s
    }

    private func label(_ i: Int, seg: Double, r: CGFloat) -> some View {
        let mid: Double = Double(i) * seg + seg / 2.0
        let fontSize: CGFloat = titles.count > 20 ? 7 : 9
        return Text(Self.short(titles[i]))
            .font(.system(size: fontSize, weight: .bold))
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(0.8), radius: 1)
            .lineLimit(1)
            .frame(width: r * 0.62, alignment: .trailing)
            .offset(x: r * 0.5)
            .rotationEffect(.degrees(mid - 90))
    }
}

fileprivate struct LVRouletteSource: Hashable {
    let key: String
    let label: String
}

struct RouletteView: View {
    @Environment(AppState.self) private var app
    @State private var source = "not_started"
    @State private var publisher = "all"
    @State private var excludeCompleted = true
    @State private var mangas: [Manga] = []
    @State private var ownedIds: Set<String> = []
    @State private var completedIds: Set<String> = []
    @State private var wishIds: Set<String> = []
    @State private var loading = true
    @State private var rotation: Double = 0
    @State private var spinning = false
    @State private var lastIndex: Int?
    @State private var result: Manga?
    @State private var busy = false

    private static let sources: [LVRouletteSource] = [
        LVRouletteSource(key: "wishlist", label: "My Wishlist"),
        LVRouletteSource(key: "collection", label: "My Collection"),
        LVRouletteSource(key: "not_started", label: "Not Started"),
        LVRouletteSource(key: "all", label: "Everything")
    ]

    private var publishers: [String] {
        let set = Set(mangas.compactMap { nonEmpty($0.publisher) })
        return set.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private var eligible: [Manga] {
        var list = mangas
        switch source {
        case "wishlist": list = list.filter { wishIds.contains($0.id) }
        case "collection": list = list.filter { ownedIds.contains($0.id) }
        case "not_started": list = list.filter { !ownedIds.contains($0.id) }
        default: break
        }
        if excludeCompleted { list = list.filter { !completedIds.contains($0.id) } }
        if publisher != "all" { list = list.filter { $0.publisher == publisher } }
        return list
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Roulette", eyebrowIcon: "circle.dotted.circle", lead: "MANGA", accent: "ROULETTE",
                             subtitle: "Spin the wheel to discover your next read from real manga in the vault.")
                filters
                if loading && mangas.isEmpty {
                    LoadingView()
                } else {
                    wheelArea
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Manga Roulette")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Self.sources, id: \.key) { s in
                        Chip(title: s.label, selected: source == s.key) {
                            source = s.key
                            result = nil
                        }
                    }
                }
            }
            HStack {
                Picker("Publisher", selection: $publisher) {
                    Text("All publishers").tag("all")
                    ForEach(publishers, id: \.self) { p in
                        Text(p).tag(p)
                    }
                }
                .pickerStyle(.menu)
                .tint(Theme.primary)
                Spacer()
                Toggle("Exclude completed", isOn: $excludeCompleted)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.muted)
                    .tint(Theme.primary)
                    .fixedSize()
            }
        }
    }

    @ViewBuilder
    private var wheelArea: some View {
        let list = eligible
        if list.isEmpty {
            EmptyState(icon: "circle.dotted.circle", text: "No eligible manga for these filters. Try another source.")
        } else {
            VStack(spacing: 16) {
                LVWheel(titles: list.map { $0.title }, rotation: rotation)
                    .frame(maxWidth: 340)
                    .padding(.top, 8)
                    .frame(maxWidth: .infinity)
                Text("\(list.count) manga on the wheel")
                    .font(.caption)
                    .foregroundStyle(Theme.muted)
                Button {
                    spin()
                } label: {
                    Label(spinning ? "Spinning..." : "Spin the wheel", systemImage: "arrow.triangle.2.circlepath")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(spinning)
                if let m = result, !spinning {
                    resultCard(m)
                }
            }
        }
    }

    private func metaLine(_ m: Manga) -> String {
        var parts: [String] = []
        if let g = nonEmpty(m.genre) { parts.append(g) }
        if let p = nonEmpty(m.publisher) { parts.append(p) }
        if m.totalVolumes > 0 { parts.append("\(m.totalVolumes) vols") }
        return parts.joined(separator: " · ")
    }

    private func resultCard(_ m: Manga) -> some View {
        let onWishlist = wishIds.contains(m.id)
        let owned = ownedIds.contains(m.id)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                CoverView(info: CoverInfo(manga: m)).frame(width: 96)
                VStack(alignment: .leading, spacing: 5) {
                    Text("The wheel chose".uppercased())
                        .font(.caption2.weight(.black))
                        .tracking(1)
                        .foregroundStyle(Theme.primary)
                    Text(m.title).font(.title3.weight(.black)).foregroundStyle(Theme.foreground)
                    Text(metaLine(m)).font(.caption).foregroundStyle(Theme.muted)
                    if let d = nonEmpty(m.description) {
                        Text(d).font(.caption).foregroundStyle(Theme.foreground.opacity(0.8)).lineLimit(3)
                    }
                }
            }
            resultButtons(m, onWishlist: onWishlist, owned: owned)
        }
        .padding(14)
        .cardStyle(highlighted: true)
    }

    private func resultButtons(_ m: Manga, onWishlist: Bool, owned: Bool) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                NavigationLink(value: Route.manga(m.id)) {
                    Label("Open Details", systemImage: "book").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                Button {
                    Task { await addToWishlist(m) }
                } label: {
                    Label(onWishlist ? "On Wishlist" : "Add to Wishlist", systemImage: onWishlist ? "heart.fill" : "heart")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle(color: onWishlist ? Theme.red : Theme.foreground))
                .disabled(onWishlist || busy)
            }
            HStack(spacing: 8) {
                if !owned {
                    Button {
                        Task { await startTracking(m) }
                    } label: {
                        Label("Start Tracking", systemImage: "plus").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle(color: Theme.primary))
                    .disabled(busy)
                }
                Button {
                    spin()
                } label: {
                    Label("Spin Again", systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle())
            }
        }
    }

    // MARK: Spin

    private func spin() {
        let list = eligible
        let n = list.count
        guard n > 0, !spinning else { return }
        var idx = Int.random(in: 0..<n)
        if n > 1, let last = lastIndex {
            while idx == last { idx = Int.random(in: 0..<n) }
        }
        lastIndex = idx
        let seg: Double = 360.0 / Double(n)
        let turns: Double = (rotation / 360.0).rounded(.up)
        let base: Double = turns * 360.0
        let center: Double = Double(idx) * seg + seg / 2.0
        let target: Double = base + 2160.0 - center
        let chosen = list[idx]
        spinning = true
        result = nil
        withAnimation(.timingCurve(0.17, 0.67, 0.12, 0.99, duration: 4.2)) {
            rotation = target
        }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(4200))
            result = chosen
            spinning = false
        }
    }

    // MARK: Data

    private func load() async {
        loading = true
        do {
            let api = Base44Client.shared
            let col = try await api.list("CollectionItem", CollectionItem.self, limit: 500, fields: ["id", "manga_id", "status", "read"]).items
            let wish = try await api.list("WishlistItem", WishlistItem.self, limit: 500, fields: ["id", "manga_id"]).items
            let fields = ["id", "title", "cover_image_url", "genre", "publisher", "description", "total_volumes", "price"]
            let ms = try await api.list("Manga", Manga.self, sort: "-created_date", limit: 120, fields: fields).items
            ownedIds = Set(col.compactMap { nonEmpty($0.manga_id) })
            completedIds = Set(col.filter { $0.effectiveStatus == .completed }.compactMap { nonEmpty($0.manga_id) })
            wishIds = Set(wish.compactMap { nonEmpty($0.manga_id) })
            mangas = ms
        } catch {
            if !lvCancelled(error) { app.fail("Could not load roulette", error) }
        }
        loading = false
    }

    private func addToWishlist(_ m: Manga) async {
        guard !wishIds.contains(m.id) else { return }
        busy = true
        defer { busy = false }
        let payload: [String: Any] = [
            "manga_id": m.id,
            "title": m.title,
            "cover_image_url": m.cover_image_url ?? "",
            "cover_seed": m.title,
            "publisher": m.publisher ?? "",
            "price": m.price ?? 0,
            "edition": "standard"
        ]
        do {
            _ = try await Base44Client.shared.create("WishlistItem", payload, WishlistItem.self)
            wishIds.insert(m.id)
            app.show("Added to wishlist")
            app.dataChanged()
        } catch {
            app.fail("Could not add to wishlist", error)
        }
    }

    private func startTracking(_ m: Manga) async {
        busy = true
        defer { busy = false }
        let payload: [String: Any] = [
            "manga_id": m.id,
            "title": "\(m.title) Vol. 1",
            "volume_number": 1,
            "cover_image_url": m.cover_image_url ?? "",
            "cover_seed": m.cover_seed ?? m.title,
            "publisher": m.publisher ?? "",
            "price": m.price ?? 0,
            "edition": "standard",
            "status": "reading",
            "read": false
        ]
        do {
            _ = try await Base44Client.shared.create("CollectionItem", payload, CollectionItem.self)
            ownedIds.insert(m.id)
            app.show("Now tracking Vol. 1")
            app.dataChanged()
        } catch {
            app.fail("Could not start tracking", error)
        }
    }
}
