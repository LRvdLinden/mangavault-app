import SwiftUI

struct AchievementsView: View {
    @Environment(AppState.self) private var app
    @State private var result: AchievementEngine.Result?
    @State private var loading = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Achievements", eyebrowIcon: "trophy.fill", lead: "ACHIEVEMENTS &", accent: "XP")
                if loading && result == nil {
                    LoadingView()
                } else if let r = result {
                    LevelCard(level: r.level, totalXP: r.totalXP)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 12) {
                        ForEach(r.evals) { e in card(e, date: r.unlockedDates[e.id]) }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Achievements")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func card(_ e: AchievementEval, date: String?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Text(e.def.icon)
                    .font(.system(size: 26))
                    .frame(width: 50, height: 50)
                    .background(e.unlocked ? Theme.primary.opacity(0.15) : Theme.secondary, in: RoundedRectangle(cornerRadius: 10))
                    .grayscale(e.unlocked ? 0 : 1)
                    .opacity(e.unlocked ? 1 : 0.6)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(e.def.name).font(.headline.weight(.black)).lineLimit(1)
                        Image(systemName: e.unlocked ? "checkmark.circle.fill" : "lock.fill")
                            .font(.caption).foregroundStyle(e.unlocked ? Theme.primary : Theme.muted)
                    }
                    Text(e.def.desc).font(.caption).foregroundStyle(Theme.muted).lineLimit(2)
                }
            }
            HStack {
                RarityBadge(rarity: e.def.rarity)
                Spacer()
                Label("+\(e.def.reward) XP", systemImage: "star.fill").font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
            }
            if !e.unlocked {
                VStack(spacing: 4) {
                    HStack { Text("\(e.value) / \(e.total)"); Spacer(); Text("\(e.percent)%") }
                        .font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                    ProgressBar(value: Double(e.percent) / 100, height: 6)
                }
            } else if let date, !date.isEmpty {
                Text("Unlocked \(date)").font(.caption2).foregroundStyle(Theme.muted)
            }
        }
        .padding(16)
        .cardStyle(highlighted: e.unlocked)
    }

    private func load() async {
        do {
            let r = try await AchievementEngine.run()
            result = r
            let fresh = r.fresh.filter { $0.kind == "achievement" }
            if !fresh.isEmpty {
                app.celebrate()
                for a in fresh { app.show("Achievement unlocked: \(a.label ?? "")", "+\(a.xp) XP") }
            }
        } catch {
            if !(error is CancellationError) { app.fail("Could not load achievements", error) }
        }
        loading = false
    }
}

struct RarityBadge: View {
    let rarity: Rarity
    var body: some View {
        let c: Color
        switch rarity {
        case .common: c = Color(white: 0.8)
        case .rare: c = Theme.sky
        case .epic: c = Theme.primary
        case .legendary: c = Theme.orange
        }
        return Text(rarity.label).font(.system(size: 10, weight: .black))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(c)
            .background(c.opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(c.opacity(0.5)))
    }
}

struct LevelCard: View {
    let level: LevelInfo
    let totalXP: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading) {
                    Text("LEVEL").font(.caption.weight(.bold)).tracking(2).foregroundStyle(Theme.muted)
                    Text("\(level.level)").font(.system(size: 52, weight: .black)).foregroundStyle(Theme.primary)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("TOTAL XP").font(.caption.weight(.bold)).tracking(2).foregroundStyle(Theme.muted)
                    Text(totalXP.formatted()).font(.title.weight(.black))
                }
            }
            HStack {
                Text("\(level.into) / \(level.span) XP to Level \(level.level + 1)")
                Spacer()
                Text("\(level.percent)%")
            }
            .font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
            ProgressBar(value: level.progress, height: 12)
        }
        .padding(20)
        .cardStyle()
    }
}

// MARK: - Daily & weekly quests (claims go through the server `rewards` function)

struct QuestsView: View {
    @Environment(AppState.self) private var app
    @State private var daily: [QuestState] = []
    @State private var weekly: [QuestState] = []
    @State private var dailyClaimed: Set<String> = []
    @State private var weeklyClaimed: Set<String> = []
    @State private var loading = true
    @State private var claiming: String?

    private var weekEnd: String {
        let end = Calendar.current.date(byAdding: .day, value: 6, to: DayKey.weekStart()) ?? Date()
        return end.formatted(.dateTime.month(.abbreviated).day())
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Quests", eyebrowIcon: "target", lead: "DAILY &", accent: "WEEKLY QUESTS")
                if loading && daily.isEmpty {
                    LoadingView()
                } else {
                    sectionHeader("DAILY", icon: "clock", note: "Resets at 00:00 Amsterdam time")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 12) {
                        ForEach(daily) { q in card(q, claimed: dailyClaimed.contains(q.id), key: "d-" + q.id) { await claim(q, scope: "daily") } }
                    }
                    sectionHeader("WEEKLY", icon: "calendar", note: "Resets Sunday end of week")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 12) {
                        ForEach(weekly) { q in card(q, claimed: weeklyClaimed.contains(q.id), key: "w-" + q.id) { await claim(q, scope: "weekly") } }
                    }
                    Text("Week runs until \(weekEnd). Progress is derived from your real activity — no fake completions.")
                        .font(.caption).foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Quests")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task(id: app.dataVersion) { await load() }
    }

    private func questTitle(_ title: String) -> Text {
        let first: Text = Text(title + " ").foregroundStyle(Theme.foreground)
        let second: Text = Text("QUESTS").foregroundStyle(Theme.primary)
        return first + second
    }

    private func sectionHeader(_ title: String, icon: String, note: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: icon).foregroundStyle(Theme.primary)
                questTitle(title)
                    .font(.title3.weight(.black))
            }
            Text(note).font(.caption).foregroundStyle(Theme.muted)
        }
        .padding(.top, 6)
    }

    private func card(_ q: QuestState, claimed isClaimed: Bool, key: String, action: @escaping () async -> Void) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: q.def.bonus ? "sparkles" : "target").foregroundStyle(Theme.primary)
                Text(q.def.name).font(.headline.weight(.black))
                Spacer()
                if isClaimed {
                    Badge(text: "Claimed", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                } else if q.def.bonus {
                    Badge(text: "BONUS", fg: Theme.primary, bg: Theme.primary.opacity(0.2), ring: Theme.primary.opacity(0.4))
                }
            }
            Text(q.def.desc).font(.subheadline).foregroundStyle(Theme.muted)
            HStack {
                Text("\(q.value) / \(q.def.total)")
                Spacer()
                Label("\(q.def.reward) XP", systemImage: "star.fill").foregroundStyle(Theme.primary)
            }
            .font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
            ProgressBar(value: Double(q.percent) / 100)
            Button { Task { await action() } } label: {
                Group {
                    if claiming == key { Text("Claiming...") }
                    else { Text(isClaimed ? "Claimed" : (q.completed ? "Claim Reward" : "In Progress")) }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .opacity(!q.completed || isClaimed ? 0.5 : 1)
            .disabled(!q.completed || isClaimed || claiming != nil)
        }
        .padding(18)
        .background(q.def.bonus ? Theme.primary.opacity(0.05) : Theme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(q.def.bonus ? Theme.primary.opacity(0.4) : Theme.border))
    }

    /// Local progress (like the web app), overridden by the server's value/completed/claimed.
    private func load() async {
        do {
            let today = DayKey.local()
            let weekStartKey = DayKey.local(DayKey.weekStart())
            let acts = try await VaultService.activities(limit: 2000)

            var dc: [String: Int] = [:]
            acts.filter { $0.day == today }.forEach { dc[$0.type ?? "", default: 0] += 1 }
            let weekActs = acts.filter { $0.day >= weekStartKey }
            var wc: [String: Int] = [:]
            weekActs.forEach { wc[$0.type ?? "", default: 0] += 1 }
            wc["activeDays"] = Set(weekActs.map(\.day)).count

            let state = try await Engines.rewardsState()
            daily = merge(QuestDef.all, dc, state.quests ?? [])
            weekly = merge(QuestDef.weekly, wc, state.weekly ?? [])
            dailyClaimed = Set((state.quests ?? []).filter { $0.claimed == true }.map(\.id))
            weeklyClaimed = Set((state.weekly ?? []).filter { $0.claimed == true }.map(\.id))
        } catch {
            if !(error is CancellationError) { app.fail("Could not load quests", error) }
        }
        loading = false
    }

    private func merge(_ defs: [QuestDef], _ local: [String: Int], _ server: [ServerQuest]) -> [QuestState] {
        defs.map { def in
            if let s = server.first(where: { $0.id == def.id }) {
                return QuestState(def: def, value: min(s.value, def.total), serverCompleted: s.completed)
            }
            return QuestState(def: def, value: min(local[def.key] ?? 0, def.total))
        }
    }

    private func claim(_ q: QuestState, scope: String) async {
        guard claiming == nil else { return }
        claiming = (scope == "weekly" ? "w-" : "d-") + q.id
        defer { claiming = nil }
        do {
            let xp = try await Engines.claimQuest(scope: scope, questId: q.id)
            if scope == "weekly" { weeklyClaimed.insert(q.id) } else { dailyClaimed.insert(q.id) }
            app.celebrate()
            app.show("\(scope == "weekly" ? "Weekly" : "Daily") quest complete: +\(xp) XP")
            app.dataChanged()
        } catch { app.fail("Could not claim", error) }
    }
}
