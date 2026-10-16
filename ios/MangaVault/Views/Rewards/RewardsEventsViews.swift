import SwiftUI

// Rewards, Vault Capsules, Trading Cards and Community Events screens.
// Ports of the web app pages /rewards, /capsules, /cards, /events, /event/:id,
// /christmas-event and /boruto-event. All XP / reward logic lives on the server
// (functions `rewards`, `vaultCapsules`, `communityEngine`, `eventEngine`).

// MARK: - Shared helpers

/// Event dates are plain `yyyy-MM-dd` strings.
fileprivate enum RVDay {
    static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func parse(_ s: String?) -> Date? {
        guard let s = nonEmpty(s) else { return nil }
        return formatter.date(from: String(s.prefix(10)))
    }

    /// Start of the day after the given date (i.e. end of that day).
    static func endOfDay(_ s: String?) -> Date? {
        guard let d = parse(s) else { return nil }
        return Calendar.current.date(byAdding: .day, value: 1, to: d)
    }

    static var today: Date { Calendar.current.startOfDay(for: Date()) }
}

/// Decodes (and ignores) any JSON body — used for create calls whose result we don't need.
fileprivate struct RVIgnored: Decodable {
    init(from decoder: Decoder) throws {}
}

/// Decodes a nested JSON value (e.g. `obj["participation"]`) into a model.
fileprivate func rvDecode<T: Decodable>(_ type: T.Type, _ value: Any?) -> T? {
    guard let value = value, JSONSerialization.isValidJSONObject(value),
          let data = try? JSONSerialization.data(withJSONObject: value, options: []) else { return nil }
    return try? JSONDecoder().decode(T.self, from: data)
}

fileprivate func rvError(_ obj: [String: Any], _ fallback: String) -> String {
    nonEmpty(obj["error"] as? String) ?? fallback
}

fileprivate func rvDateRange(_ e: CommunityEvent) -> String {
    let start = [e.start_date, e.start_time].compactMap { nonEmpty($0) }.joined(separator: " ")
    let end = [e.end_date, e.end_time].compactMap { nonEmpty($0) }.joined(separator: " ")
    var s = "\(start) → \(end)"
    if let tz = nonEmpty(e.timezone) { s += " (\(tz))" }
    return s
}

fileprivate let rvTierOrder: [String] = ["common", "rare", "epic", "legendary", "event"]

fileprivate func rvTierRank(_ t: String?) -> Int {
    rvTierOrder.firstIndex(of: t ?? "") ?? rvTierOrder.count
}

fileprivate func rvTierColors(_ tier: String?) -> [Color] {
    switch tier ?? "" {
    case "rare": return [Theme.sky, Color(hex: "#0369a1")]
    case "epic": return [Theme.orange, Theme.red]
    case "legendary": return [Theme.amber, Theme.primary]
    case "event": return [Theme.primary, Theme.red]
    default: return [Color(white: 0.55), Color(white: 0.22)]
    }
}

fileprivate func rvPercent(_ v: Double) -> String {
    let p = v <= 1 ? v * 100 : v
    if p == p.rounded() { return "\(Int(p))%" }
    return String(format: "%.1f%%", p)
}

fileprivate func rvRarityRank(_ r: String) -> Int {
    CardRarity.allCases.firstIndex(where: { $0.rawValue == r }) ?? 99
}

fileprivate struct RVErrorState: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle").font(.system(size: 40)).foregroundStyle(Theme.red.opacity(0.8))
            Text(message).font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            Button("Try again", action: retry).buttonStyle(PrimaryButtonStyle())
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 50)
    }
}

fileprivate struct RVSectionTitle: View {
    let title: String
    var icon: String? = nil
    var color: Color = Theme.primary

    var body: some View {
        HStack(spacing: 6) {
            if let icon { Image(systemName: icon).foregroundStyle(color) }
            Text(title.uppercased()).font(.footnote.weight(.black)).tracking(1).foregroundStyle(Theme.foreground)
        }
    }
}

fileprivate struct RVRarityPill: View {
    let rarity: String?

    var body: some View {
        let r = CardRarity.of(rarity)
        let c = Color(hex: r.hex)
        return Text(r.label).font(.system(size: 10, weight: .black))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(c)
            .background(c.opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(c.opacity(0.5)))
    }
}

fileprivate struct RVNotice: View {
    let text: String
    var color: Color = Theme.sky
    var icon: String = "info.circle.fill"

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(color)
            Text(text).font(.caption).foregroundStyle(Theme.foreground)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.4)))
    }
}

/// Hero background: image (or accent gradient), dark gradient and optional snow.
fileprivate struct RVHeroBackground: View {
    let image: String?
    let accent: Color
    var snow = false
    var lights = false

    var body: some View {
        ZStack {
            if let image {
                RemoteImage(url: image)
            } else {
                LinearGradient(colors: [accent, Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
            }
            LinearGradient(colors: [.black.opacity(0.92), .black.opacity(0.45)], startPoint: .bottom, endPoint: .top)
            if snow { SnowLayer(lights: lights) }
        }
    }
}

fileprivate struct RVLeaderboardList: View {
    let rows: [LeaderboardRow]?
    var target: Int = 0
    var suffix: String = ""
    var accent: Color = Theme.primary

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let rows {
                if rows.isEmpty {
                    Text("No participants yet.").font(.caption).foregroundStyle(Theme.muted)
                } else {
                    ForEach(Array(rows.prefix(8).enumerated()), id: \.offset) { pair in
                        row(rank: pair.offset + 1, pair.element)
                    }
                }
            } else {
                ProgressView().tint(Theme.muted).frame(maxWidth: .infinity)
            }
        }
    }

    private func row(rank: Int, _ r: LeaderboardRow) -> some View {
        let value = target > 0 ? "\(r.progress)/\(target)" : "\(r.progress)"
        return HStack(spacing: 8) {
            Text("#\(rank)").font(.caption.weight(.black)).foregroundStyle(rank <= 3 ? accent : Theme.muted)
                .frame(width: 30, alignment: .leading)
            Text(nonEmpty(r.name) ?? "Reader").font(.caption.weight(.bold)).lineLimit(1)
            Spacer()
            Text(suffix.isEmpty ? value : "\(value) \(suffix)").font(.caption.weight(.black)).foregroundStyle(Theme.foreground)
        }
        .padding(.vertical, 6).padding(.horizontal, 10)
        .background(Theme.secondary.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Rewards

struct RewardsView: View {
    @Environment(AppState.self) private var app
    @State private var state: RewardsState?
    @State private var loading = true
    @State private var errorText: String?
    @State private var claiming = false
    @State private var history: [RewardHistoryItem] = []
    @State private var historyCursor: String?
    @State private var historyHasMore = false
    @State private var historyLoading = false
    @State private var historyVersion = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Rewards", eyebrowIcon: "gift.fill", lead: "MY", accent: "REWARDS",
                             subtitle: "Daily rewards, level rewards and everything you earned.")
                if loading && state == nil {
                    LoadingView()
                } else if let s = state {
                    dailyCard(s)
                    levelCard(s)
                    historyCard
                } else {
                    RVErrorState(message: errorText ?? "Could not load your rewards.") {
                        Task { await load() }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Rewards")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable {
            await load()
            historyVersion += 1
        }
        .task { await load() }
        .task(id: historyVersion) { await loadHistory(reset: true) }
    }

    // Daily reward

    private func dailyCard(_ s: RewardsState) -> some View {
        let claimed = s.daily?.claimedToday ?? false
        let streak = s.daily?.streak ?? 0
        let xp = s.daily?.xp ?? 0
        let capsule = (s.daily?.capsule ?? false) && !claimed
        let note = claimed
            ? "Next reward available tomorrow at 00:00 (Amsterdam time)."
            : "A new day starts at 00:00 Amsterdam time. Missing a day only resets the streak bonus."
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Daily Reward", icon: "calendar.badge.plus")
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Streak: \(streak) day\(streak == 1 ? "" : "s")").font(.headline.weight(.black))
                    Text("+\(xp) XP" + (capsule ? " + Capsule" : ""))
                        .font(.subheadline.weight(.bold)).foregroundStyle(Theme.primary)
                }
                Spacer()
                Image(systemName: "flame.fill").font(.title).foregroundStyle(streak > 0 ? Theme.orange : Theme.muted)
            }
            Button {
                Task { await claimDaily() }
            } label: {
                Text(claimed ? "Claimed today" : (claiming ? "Claiming…" : "Claim Daily Reward"))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle(color: claimed ? Theme.secondary : Theme.primary))
            .disabled(claimed || claiming)
            Text(note).font(.caption).foregroundStyle(Theme.muted)
        }
        .padding(16)
        .cardStyle(highlighted: !claimed)
    }

    // Level rewards

    private func levelCard(_ s: RewardsState) -> some View {
        let info = s.level.info
        let rewards = s.levelRewards ?? []
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Level Rewards", icon: "star.circle.fill")
            Text("You are level \(info.level) · \(info.into)/\(info.span) XP to next level")
                .font(.subheadline.weight(.bold)).foregroundStyle(Theme.muted)
            ProgressBar(value: info.progress, height: 8)
            ForEach(rewards) { r in levelRow(r) }
            NavigationLink(value: Route.capsules) {
                Text("Open unlocked capsules →").font(.subheadline.weight(.bold)).foregroundStyle(Theme.primary)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .cardStyle()
    }

    private func levelRow(_ r: LevelReward) -> some View {
        let unlocked = r.unlocked ?? false
        return HStack(spacing: 12) {
            Image(systemName: unlocked ? "checkmark.circle.fill" : "lock.fill")
                .foregroundStyle(unlocked ? Theme.primary : Theme.muted)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 1) {
                Text("Level \(r.level)").font(.caption.weight(.black)).foregroundStyle(unlocked ? Theme.primary : Theme.muted)
                Text(r.label ?? "").font(.subheadline.weight(.bold)).foregroundStyle(unlocked ? Theme.foreground : Theme.muted)
            }
            Spacer()
        }
        .padding(10)
        .background(unlocked ? Theme.primary.opacity(0.1) : Theme.secondary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(unlocked ? Theme.primary.opacity(0.4) : Color.clear))
    }

    // History

    private var historyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            RVSectionTitle(title: "Reward History", icon: "clock.arrow.circlepath")
            if history.isEmpty && historyLoading {
                ProgressView().tint(Theme.muted).frame(maxWidth: .infinity).padding(.vertical, 12)
            } else if history.isEmpty {
                Text("No rewards yet.").font(.subheadline).foregroundStyle(Theme.muted)
            } else {
                ForEach(history) { item in historyRow(item) }
                if historyHasMore {
                    Button {
                        Task { await loadHistory(reset: false) }
                    } label: {
                        Text(historyLoading ? "Loading…" : "Load more").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle())
                    .disabled(historyLoading)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func historyRow(_ item: RewardHistoryItem) -> some View {
        let date = ServerDate.parse(nonEmpty(item.granted_at) ?? item.created_date)
        let dateText = date.map { $0.formatted(date: .abbreviated, time: .shortened) } ?? ""
        let kind = RewardHistoryItem.kindLabels[item.kind ?? ""] ?? (item.kind ?? "")
        let duplicate = item.status == "duplicate"
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(dateText).font(.caption2).foregroundStyle(Theme.muted)
                Spacer()
                Text(duplicate ? "Duplicate (ignored)" : "Granted")
                    .font(.caption2.weight(.black))
                    .foregroundStyle(duplicate ? Theme.muted : Theme.emerald)
            }
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(kind).font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
                    Text(item.label ?? "").font(.subheadline.weight(.bold)).lineLimit(2)
                }
                Spacer()
                Text(item.xp > 0 ? "+\(item.xp) XP" : "—").font(.subheadline.weight(.black))
            }
        }
        .padding(10)
        .background(Theme.secondary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
    }

    // Data

    private func load() async {
        do {
            let s = try await Engines.rewardsState()
            state = s
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func loadHistory(reset: Bool) async {
        if historyLoading && !reset { return }
        historyLoading = true
        defer { historyLoading = false }
        do {
            let page = try await Engines.rewardHistory(cursor: reset ? nil : historyCursor)
            if reset { history = page.items } else { history += page.items }
            historyCursor = nonEmpty(page.next_cursor)
            historyHasMore = (page.has_more ?? true) && historyCursor != nil
        } catch {
            if !(error is CancellationError) && !reset { app.fail("Could not load reward history", error) }
        }
    }

    private func claimDaily() async {
        guard !claiming else { return }
        claiming = true
        defer { claiming = false }
        do {
            let r = try await Engines.claimDaily()
            app.show("Daily reward claimed: +\(r.xp) XP", r.capsule ? "Streak bonus: a Vault Capsule was added!" : nil)
            app.celebrate()
            if let st = r.state { state = st } else { await load() }
            historyVersion += 1
            app.dataChanged()
        } catch {
            app.fail("Could not claim daily reward", error)
        }
    }
}

// MARK: - Vault Capsules

fileprivate struct RVRevealInfo {
    var capsuleId: String
    var tier: String
    var reward: CapsuleReward?
    var ready: Bool
}

fileprivate struct RVCapsuleReveal: View {
    let tier: String
    let reward: CapsuleReward?
    let ready: Bool
    let onReveal: () -> Void
    let onContinue: () -> Void

    @State private var elapsed = false
    @State private var pulse = false
    @State private var revealed = false

    private var showReward: Bool { ready && elapsed }

    var body: some View {
        ZStack {
            Color.black.opacity(0.9).ignoresSafeArea()
            if showReward {
                rewardView
            } else {
                chargingView
            }
        }
        .task {
            try? await Task.sleep(for: .seconds(1.4))
            elapsed = true
        }
        .onChange(of: showReward) { _, value in
            if value && !revealed {
                revealed = true
                onReveal()
            }
        }
    }

    private var chargingView: some View {
        VStack(spacing: 24) {
            Image(systemName: "shippingbox.fill")
                .font(.system(size: 90))
                .foregroundStyle(LinearGradient(colors: rvTierColors(tier), startPoint: .topLeading, endPoint: .bottomTrailing))
                .scaleEffect(pulse ? 1.15 : 0.9)
                .shadow(color: (rvTierColors(tier).first ?? Theme.primary).opacity(0.8), radius: pulse ? 30 : 10)
                .animation(.easeInOut(duration: 0.35).repeatForever(autoreverses: true), value: pulse)
                .onAppear { pulse = true }
            Text("Opening \(tier.capitalized) capsule…").font(.headline.weight(.black)).foregroundStyle(.white)
            Button("Skip") { elapsed = true }
                .buttonStyle(OutlineButtonStyle())
        }
    }

    private var rewardView: some View {
        let isCard = reward?.kind == "card"
        let isXP = reward?.kind == "xp"
        let color = Color(hex: CardRarity.of(reward?.rarity).hex)
        return VStack(spacing: 16) {
            Group {
                if isXP {
                    LinearGradient(colors: [Theme.primary, Theme.red], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .overlay(Text("XP").font(.system(size: 54, weight: .black)).foregroundStyle(.white))
                } else if let url = nonEmpty(reward?.artwork_url) {
                    RemoteImage(url: url)
                } else {
                    Theme.secondary.overlay(Text("No image").font(.caption).foregroundStyle(Theme.muted))
                }
            }
            .frame(width: 180, height: 252)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(color, lineWidth: 2))
            .shadow(color: color.opacity(0.6), radius: 20)

            if nonEmpty(reward?.rarity) != nil { RVRarityPill(rarity: reward?.rarity) }
            Text(reward?.title ?? "Reward").font(.title3.weight(.black)).foregroundStyle(.white).multilineTextAlignment(.center)
            Text(isCard ? "Added to your Trading Cards" : "You already own every card at this rarity, so you got Vault XP instead.")
                .font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            Button {
                onContinue()
            } label: {
                Text("Continue").frame(maxWidth: 220)
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        .padding(24)
    }
}

struct CapsulesView: View {
    @Environment(AppState.self) private var app
    @State private var data: CapsulesState?
    @State private var loading = true
    @State private var errorText: String?
    @State private var reveal: RVRevealInfo?

    private var sealed: [VaultCapsule] {
        (data?.capsules ?? []).filter { !($0.opened ?? false) }
            .sorted { rvTierRank($0.tier) < rvTierRank($1.tier) }
    }

    private var opened: [VaultCapsule] {
        let all: [VaultCapsule] = data?.capsules ?? []
        let openedOnly: [VaultCapsule] = all.filter { $0.opened ?? false }
        return openedOnly.sorted { Self.openedSortKey($0) > Self.openedSortKey($1) }
    }

    private static func openedSortKey(_ c: VaultCapsule) -> String {
        if let d = nonEmpty(c.opened_date) { return d }
        return c.created_date ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Capsules", eyebrowIcon: "shippingbox.fill", lead: "VAULT", accent: "CAPSULES",
                             subtitle: "Free capsules earned through collecting, achievements, daily quests and events. No payments, ever.")
                if loading && data == nil {
                    LoadingView()
                } else if let d = data {
                    sealedSection
                    oddsSection(d)
                    openedSection
                } else {
                    RVErrorState(message: errorText ?? "Could not load your capsules.") {
                        Task { await load() }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Vault Capsules")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .fullScreenCover(isPresented: Binding(get: { reveal != nil }, set: { if !$0 { reveal = nil } })) {
            if let r = reveal {
                RVCapsuleReveal(tier: r.tier, reward: r.reward, ready: r.ready,
                                onReveal: { app.celebrate() },
                                onContinue: { reveal = nil })
                    .presentationBackground(Color.clear)
            }
        }
    }

    private var sealedSection: some View {
        let list = sealed
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Sealed (\(list.count))", icon: "shippingbox")
            if list.isEmpty {
                Text("No sealed capsules. Add volumes, finish daily quests or complete events to earn more.")
                    .font(.subheadline).foregroundStyle(Theme.muted)
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
            } else {
                LazyVGrid(columns: smallGridColumns, spacing: 12) {
                    ForEach(list) { c in capsuleTile(c) }
                }
            }
        }
    }

    private func capsuleTile(_ c: VaultCapsule) -> some View {
        let tier = c.tier ?? "common"
        let source = (c.source ?? "").replacingOccurrences(of: "_", with: " ").capitalized
        return Button {
            Task { await openCapsule(c) }
        } label: {
            VStack(spacing: 8) {
                Image(systemName: "shippingbox.fill").font(.system(size: 34)).foregroundStyle(.white)
                Text("\(tier.capitalized) capsule").font(.caption.weight(.black)).foregroundStyle(.white)
                if !source.isEmpty {
                    Text(source).font(.caption2).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 130)
            .background(LinearGradient(colors: rvTierColors(tier), startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.2)))
            .shadow(color: (rvTierColors(tier).first ?? Theme.primary).opacity(0.4), radius: 8, y: 4)
        }
        .buttonStyle(.plain)
        .disabled(reveal != nil)
    }

    private func oddsSection(_ d: CapsulesState) -> some View {
        let tiers = d.odds.keys.sorted { rvTierRank($0) < rvTierRank($1) }
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Drop rates", icon: "percent")
            if tiers.isEmpty {
                Text("Drop rates are not available right now.").font(.caption).foregroundStyle(Theme.muted)
            }
            ForEach(tiers, id: \.self) { tier in
                oddsRow(tier, odds: d.odds[tier] ?? [:], fallback: d.fallback_xp[tier])
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func oddsRow(_ tier: String, odds: [String: Double], fallback: Int?) -> some View {
        let rarities = odds.keys.sorted { rvRarityRank($0) < rvRarityRank($1) }
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Circle().fill(rvTierColors(tier).first ?? Theme.muted).frame(width: 8, height: 8)
                Text(tier.capitalized).font(.subheadline.weight(.black))
                Spacer()
                if let fallback {
                    Text("Duplicate: \(fallback) XP").font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
              HStack(spacing: 6) {
                ForEach(rarities, id: \.self) { r in
                    Text("\(CardRarity.of(r).label) \(rvPercent(odds[r] ?? 0))")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .foregroundStyle(Color(hex: CardRarity.of(r).hex))
                        .background(Color(hex: CardRarity.of(r).hex).opacity(0.12), in: RoundedRectangle(cornerRadius: 4))
                }
              }
            }
        }
    }

    private var openedSection: some View {
        let list = opened
        return VStack(alignment: .leading, spacing: 10) {
            RVSectionTitle(title: "Opened (\(list.count))", icon: "shippingbox.and.arrow.backward")
            if list.isEmpty {
                Text("No opened capsules yet.").font(.caption).foregroundStyle(Theme.muted)
            }
            ForEach(list) { c in openedRow(c) }
        }
        .padding(16)
        .cardStyle()
    }

    private func openedRow(_ c: VaultCapsule) -> some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 6)
                .fill(LinearGradient(colors: rvTierColors(c.tier), startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 30, height: 30)
                .overlay(Image(systemName: "shippingbox").font(.caption).foregroundStyle(.white))
            VStack(alignment: .leading, spacing: 1) {
                Text("\((c.tier ?? "common").capitalized) capsule").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Text(c.reward?.title ?? "Opening…").font(.subheadline.weight(.bold)).lineLimit(1)
            }
            Spacer()
            if c.reward?.kind == "xp" {
                Text("XP").font(.caption.weight(.black)).foregroundStyle(Theme.primary)
            } else if nonEmpty(c.reward?.rarity) != nil {
                RVRarityPill(rarity: c.reward?.rarity)
            }
        }
    }

    private func load() async {
        do {
            let d = try await Engines.capsules()
            data = d
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func openCapsule(_ c: VaultCapsule) async {
        guard reveal == nil else { return }
        reveal = RVRevealInfo(capsuleId: c.id, tier: c.tier ?? "common", reward: nil, ready: false)
        do {
            let openedCapsule = try await Engines.openCapsule(c.id)
            reveal?.reward = openedCapsule?.reward
            reveal?.ready = true
            app.dataChanged()
            await load()
        } catch {
            reveal = nil
            app.fail("Could not open capsule", error)
        }
    }
}

// MARK: - Trading Cards

fileprivate struct RVOwnedCard {
    var id: String
    var count: Int
    var favorite: Bool
    var source: String?
}

fileprivate struct RVCardDetailSheet: View {
    let card: TradingCard
    let owned: RVOwnedCard?
    let onFavorite: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    TradingCardView(card: card, owned: owned != nil, count: owned?.count ?? 0,
                                    favorite: owned?.favorite ?? false, size: .lg)
                        .padding(.top, 8)
                    Text(card.title).font(.title2.weight(.black)).multilineTextAlignment(.center)
                    RVRarityPill(rarity: card.rarity)
                    Text(nonEmpty(card.description) ?? "A rare collectible from the MangaVault universe.")
                        .font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
                    if let flavor = nonEmpty(card.flavor) {
                        Text("\u{201C}\(flavor)\u{201D}").italic().font(.subheadline).foregroundStyle(Theme.foreground)
                            .multilineTextAlignment(.center)
                    }
                    details
                    if owned != nil {
                        Button {
                            onFavorite()
                        } label: {
                            Label((owned?.favorite ?? false) ? "Favorite" : "Add to favorites",
                                  systemImage: (owned?.favorite ?? false) ? "star.fill" : "star")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(PrimaryButtonStyle(color: (owned?.favorite ?? false) ? Theme.primary : Theme.secondary))
                    }
                }
                .padding(20)
            }
            .background(Theme.background)
            .navigationTitle("Card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } }
            }
        }
    }

    private var details: some View {
        let status = owned.map { "Status: Owned ×\($0.count)" } ?? "Not collected yet"
        return VStack(alignment: .leading, spacing: 6) {
            Text("Category: \((nonEmpty(card.category) ?? "—").capitalized)")
            Text(status).foregroundStyle(owned != nil ? Theme.emerald : Theme.muted)
        }
        .font(.caption.weight(.bold))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .cardStyle()
    }
}

struct CardsView: View {
    @Environment(AppState.self) private var app
    @State private var cards: [TradingCard] = []
    @State private var owned: [String: RVOwnedCard] = [:]
    @State private var loading = true
    @State private var errorText: String?
    @State private var search = ""
    @State private var rarity: CardRarity?
    @State private var favoritesOnly = false
    @State private var detail: TradingCard?
    @State private var claimingStarter = false

    private var filtered: [TradingCard] {
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        return cards.filter { c in
            if let r = rarity, c.rarityValue != r { return false }
            if favoritesOnly && !(owned[c.card_id]?.favorite ?? false) { return false }
            if q.isEmpty { return true }
            return c.title.lowercased().contains(q) || (c.description ?? "").lowercased().contains(q)
        }
    }

    private var hasStarter: Bool {
        owned.values.contains { $0.source == "starter" }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Cards", eyebrowIcon: "rectangle.stack.fill", lead: "TRADING", accent: "CARDS",
                             subtitle: "Collect manga cards by reading, completing events, and joining the community.")
                if loading && cards.isEmpty {
                    LoadingView()
                } else if let errorText, cards.isEmpty {
                    RVErrorState(message: errorText) { Task { await load() } }
                } else {
                    stats
                    if !hasStarter { starterBanner }
                    filters
                    grid
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Trading Cards")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, prompt: "Search cards…")
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $detail) { c in
            RVCardDetailSheet(card: c, owned: owned[c.card_id], onFavorite: { toggleFavorite(c) })
        }
    }

    private var stats: some View {
        let total = cards.count
        let ownedCount = cards.filter { owned[$0.card_id] != nil }.count
        let ratio: Double = total > 0 ? Double(ownedCount) / Double(total) : 0
        let pct: Int = Int((ratio * 100).rounded())
        return HStack(spacing: 10) {
            StatCard(icon: "rectangle.stack", label: "Collected", value: "\(ownedCount)/\(total)")
            StatCard(icon: "chart.pie", label: "Complete", value: "\(pct)%", accent: Theme.emerald)
        }
    }

    private var starterBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "gift.fill").font(.title2).foregroundStyle(Theme.primary)
            VStack(alignment: .leading, spacing: 2) {
                Text("Free Starter Pack").font(.headline.weight(.black))
                Text("Claim 3 common cards to begin your collection.").font(.caption).foregroundStyle(Theme.muted)
            }
            Spacer(minLength: 4)
            Button {
                Task { await claimStarter() }
            } label: {
                Text(claimingStarter ? "Claiming…" : "Claim Pack")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(claimingStarter)
        }
        .padding(14)
        .background(Theme.primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.primary.opacity(0.45)))
    }

    private var filters: some View {
        HStack(spacing: 8) {
            Menu {
                Button("All rarities") { rarity = nil }
                ForEach(CardRarity.allCases) { r in
                    Button(r.label) { rarity = r }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(rarity?.label ?? "All rarities")
                    Image(systemName: "chevron.down")
                }
                .font(.caption.weight(.bold))
                .padding(.horizontal, 12).padding(.vertical, 7)
                .foregroundStyle(rarity == nil ? Theme.muted : Color.white)
                .background(rarity == nil ? Theme.card : Theme.primary, in: RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(rarity == nil ? Theme.border : Color.clear))
            }
            Chip(title: "Favorites", icon: favoritesOnly ? "star.fill" : "star", selected: favoritesOnly) {
                favoritesOnly.toggle()
            }
            Spacer()
        }
    }

    @ViewBuilder private var grid: some View {
        let list = filtered
        if list.isEmpty {
            EmptyState(icon: "rectangle.stack", text: "No cards match your filters.")
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 14)], spacing: 18) {
                ForEach(list) { c in cardCell(c) }
            }
        }
    }

    private func cardCell(_ c: TradingCard) -> some View {
        let o = owned[c.card_id]
        var favAction: (() -> Void)? = nil
        if o != nil { favAction = { toggleFavorite(c) } }
        return TradingCardView(card: c, owned: o != nil, count: o?.count ?? 0, favorite: o?.favorite ?? false,
                               size: .md, onTap: { detail = c }, onFavorite: favAction)
            .frame(maxWidth: .infinity)
    }

    private func load() async {
        do {
            let all = try await Engines.allCards()
            let mine = try await Engines.myCards()
            var map: [String: RVOwnedCard] = [:]
            for u in mine where !u.card_id.isEmpty {
                if var existing = map[u.card_id] {
                    existing.count += max(u.count, 1)
                    existing.favorite = existing.favorite || (u.favorite ?? false)
                    if existing.source == nil { existing.source = u.source }
                    map[u.card_id] = existing
                } else {
                    map[u.card_id] = RVOwnedCard(id: u.id, count: max(u.count, 1), favorite: u.favorite ?? false, source: u.source)
                }
            }
            cards = all
            owned = map
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func claimStarter() async {
        guard !claimingStarter else { return }
        claimingStarter = true
        defer { claimingStarter = false }
        do {
            let d = try await Engines.community("starter")
            let o = Engines.json(d)
            if (o["ok"] as? Bool) == true {
                let n = (o["granted"] as? [Any])?.count ?? 0
                app.show("Starter pack claimed!", "\(n) cards added")
                app.celebrate()
                app.dataChanged()
                await load()
            } else {
                app.show("Could not claim starter pack", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not claim starter pack", error)
        }
    }

    private func toggleFavorite(_ card: TradingCard) {
        guard var o = owned[card.card_id] else { return }
        let newValue = !o.favorite
        o.favorite = newValue
        owned[card.card_id] = o
        let userCardId = o.id
        let cardId = card.card_id
        Task {
            do {
                try await Base44Client.shared.update("UserCard", userCardId, ["favorite": newValue])
            } catch {
                if var revert = owned[cardId] {
                    revert.favorite = !newValue
                    owned[cardId] = revert
                }
                app.fail("Could not update favorite", error)
            }
        }
    }
}

// MARK: - Community events (list)

struct EventsView: View {
    @Environment(AppState.self) private var app
    @State private var events: [CommunityEvent] = []
    @State private var participations: [String: EventParticipation] = [:]
    @State private var rewardCards: [String: TradingCard] = [:]
    @State private var loading = true
    @State private var errorText: String?
    @State private var busy: String?
    @State private var openLeaderboards: Set<String> = []
    @State private var leaderboards: [String: [LeaderboardRow]] = [:]

    private var grouped: (active: [CommunityEvent], upcoming: [CommunityEvent], completed: [CommunityEvent]) {
        let today = RVDay.today
        var active: [CommunityEvent] = []
        var upcoming: [CommunityEvent] = []
        var completed: [CommunityEvent] = []
        for e in events {
            let end = RVDay.parse(e.end_date)
            let start = RVDay.parse(e.start_date)
            let endedByDate = end.map { $0 < today } ?? false
            let futureByDate = start.map { $0 > today } ?? false
            if e.status == "completed" || endedByDate {
                completed.append(e)
            } else if e.status == "upcoming" || futureByDate {
                upcoming.append(e)
            } else {
                active.append(e)
            }
        }
        upcoming.sort { a, b in
            let da: Date = RVDay.parse(a.start_date) ?? Date.distantFuture
            let db: Date = RVDay.parse(b.start_date) ?? Date.distantFuture
            return da < db
        }
        return (active, upcoming, completed)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Events", eyebrowIcon: "calendar.badge.clock", lead: "COMMUNITY", accent: "EVENTS",
                             subtitle: "Join reading events, track real progress, and earn exclusive trading cards.")
                EventBannerView()
                if loading && events.isEmpty {
                    LoadingView()
                } else if let errorText, events.isEmpty {
                    RVErrorState(message: errorText) { Task { await load() } }
                } else {
                    sections
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Events")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private var sections: some View {
        let g = grouped
        return VStack(alignment: .leading, spacing: 18) {
            section("Active Events", icon: "bolt.fill", list: g.active, empty: "No active events at the moment.", active: true)
            section("Upcoming", icon: "calendar", list: g.upcoming, empty: "No upcoming events scheduled.", active: false)
            section("Completed", icon: "checkmark.seal", list: g.completed, empty: "No completed events yet.", active: false)
        }
    }

    private func section(_ title: String, icon: String, list: [CommunityEvent], empty: String, active: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: title, icon: icon)
            if list.isEmpty {
                Text(empty).font(.subheadline).foregroundStyle(Theme.muted)
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
            } else {
                ForEach(list) { e in eventCard(e, active: active) }
            }
        }
    }

    private func eventCard(_ e: CommunityEvent, active: Bool) -> some View {
        let accent = Color(hex: e.accent)
        return VStack(alignment: .leading, spacing: 0) {
            eventBanner(e, accent: accent, active: active)
            VStack(alignment: .leading, spacing: 10) {
                eventInfo(e, accent: accent)
                eventLinks(e, accent: accent)
                if showsLegacy(e) { legacyBlock(e, accent: accent) }
            }
            .padding(14)
        }
        .background(Theme.card.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(active ? accent.opacity(0.5) : Theme.border))
    }

    private func eventBanner(_ e: CommunityEvent, accent: Color, active: Bool) -> some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 130)
            .background {
                if let img = nonEmpty(e.banner_url) ?? e.heroImage {
                    RemoteImage(url: img)
                } else {
                    LinearGradient(colors: [accent, Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
                }
            }
            .overlay(LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom))
            .overlay(alignment: .topLeading) {
                HStack {
                    if !e.typeLabel.isEmpty {
                        Badge(text: e.typeLabel.uppercased(), fg: .white, bg: .black.opacity(0.65), ring: accent.opacity(0.6))
                    }
                    Spacer()
                    if active {
                        Badge(text: "LIVE", icon: "dot.radiowaves.left.and.right", fg: .white, bg: Theme.red)
                    }
                }
                .padding(10)
            }
            .clipped()
    }

    private func eventInfo(_ e: CommunityEvent, accent: Color) -> some View {
        let reqLabel = CommunityEvent.requirementLabels[e.requirement_type ?? ""] ?? "goals"
        return VStack(alignment: .leading, spacing: 6) {
            Text(e.displayTitle.isEmpty ? "Event" : e.displayTitle).font(.headline.weight(.black))
            if let d = nonEmpty(e.description) ?? nonEmpty(e.short_description) {
                Text(d).font(.caption).foregroundStyle(Theme.muted).lineLimit(4)
            }
            HStack(spacing: 12) {
                Label(rvDateRange(e), systemImage: "calendar").lineLimit(1).minimumScaleFactor(0.8)
                if e.requirement_value > 0 {
                    Label("\(e.requirement_value) \(reqLabel)", systemImage: "target").lineLimit(1)
                }
            }
            .font(.caption2.weight(.bold))
            .foregroundStyle(Theme.muted)
            if let rid = nonEmpty(e.reward_card_id), let card = rewardCards[rid] {
                rewardMini(card)
            }
        }
    }

    private func rewardMini(_ c: TradingCard) -> some View {
        HStack(spacing: 10) {
            Group {
                if let url = nonEmpty(c.artwork_url) {
                    RemoteImage(url: url)
                } else {
                    Theme.secondary.overlay(Image(systemName: "photo").font(.caption).foregroundStyle(Theme.muted))
                }
            }
            .frame(width: 36, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(hex: c.rarityValue.hex), lineWidth: 1))
            VStack(alignment: .leading, spacing: 3) {
                Text("REWARD CARD").font(.system(size: 9, weight: .black)).foregroundStyle(Theme.muted)
                Text(c.title).font(.caption.weight(.bold)).lineLimit(1)
                RVRarityPill(rarity: c.rarity)
            }
            Spacer()
        }
        .padding(8)
        .background(Theme.secondary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
    }

    @ViewBuilder private func eventLinks(_ e: CommunityEvent, accent: Color) -> some View {
        let kind = e.type ?? ""
        let showGeneric = e.publish_status != nil || !["special_release", "christmas_event"].contains(kind)
        if kind == "special_release" {
            NavigationLink(value: Route.borutoEvent) {
                Text("View Event Page →").font(.subheadline.weight(.bold)).foregroundStyle(accent)
            }
            .buttonStyle(.plain)
        }
        if kind == "christmas_event" {
            NavigationLink(value: Route.christmasEvent) {
                Text("View Event Page →").font(.subheadline.weight(.bold)).foregroundStyle(accent)
            }
            .buttonStyle(.plain)
        }
        if showGeneric {
            NavigationLink(value: Route.event(e.id)) {
                Text("View Event →").font(.subheadline.weight(.bold)).foregroundStyle(accent)
            }
            .buttonStyle(.plain)
        }
    }

    private func showsLegacy(_ e: CommunityEvent) -> Bool {
        e.publish_status == nil && e.type != "christmas_event" && e.type != "grand_event"
    }

    @ViewBuilder private func legacyBlock(_ e: CommunityEvent, accent: Color) -> some View {
        let isBusy = busy == e.id
        if let p = participations[e.id] {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Your progress").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                    Spacer()
                    Text("\(p.progress)/\(e.requirement_value)").font(.caption.weight(.black))
                }
                ProgressBar(value: Double(p.progress) / Double(max(e.requirement_value, 1)), height: 6, color: accent)
                HStack(spacing: 8) {
                    Button {
                        Task { await updateProgress(e) }
                    } label: {
                        Label("Update", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(OutlineButtonStyle())
                    Button {
                        toggleLeaderboard(e)
                    } label: {
                        Label("Leaderboard", systemImage: "list.number")
                    }
                    .buttonStyle(OutlineButtonStyle(color: openLeaderboards.contains(e.id) ? accent : Theme.foreground))
                    Spacer(minLength: 0)
                }
                .disabled(isBusy)
                if (p.completed ?? false) && !(p.reward_claimed ?? false) {
                    Button {
                        Task { await claimLegacy(e) }
                    } label: {
                        Text(isBusy ? "Claiming…" : "Claim Reward").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: accent))
                    .disabled(isBusy)
                }
                if p.reward_claimed ?? false {
                    Badge(text: "Reward claimed", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                }
                if openLeaderboards.contains(e.id) {
                    RVLeaderboardList(rows: leaderboards[e.id], target: e.requirement_value, accent: accent)
                }
            }
        } else {
            Button {
                Task { await join(e) }
            } label: {
                Text(isBusy ? "Joining…" : "Join Event").frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle(color: accent))
            .disabled(isBusy)
        }
    }

    // Data

    private func load() async {
        do {
            let page = try await Base44Client.shared.list("CommunityEvent", CommunityEvent.self, sort: "-start_date", limit: 50)
            let evs = page.items.filter { $0.publish_status != "draft" && $0.publish_status != "archived" }

            var parts: [String: EventParticipation] = [:]
            if let uid = app.user?.id {
                let pp = try? await Base44Client.shared.list("EventParticipation", EventParticipation.self,
                                                              query: ["user_id": uid], limit: 100,
                                                              fields: ["event_id", "progress", "completed", "reward_claimed"])
                for p in pp?.items ?? [] {
                    if let eid = nonEmpty(p.event_id) { parts[eid] = p }
                }
            }

            let ids = Array(Set(evs.compactMap { nonEmpty($0.reward_card_id) }))
            var rc: [String: TradingCard] = [:]
            if !ids.isEmpty {
                let cardQuery: [String: Any] = ["card_id": ["$in": ids]]
                let cs = try? await Base44Client.shared.list("TradingCard", TradingCard.self, query: cardQuery,
                                                             limit: max(ids.count, 1),
                                                             fields: ["card_id", "title", "rarity", "artwork_url"])
                for c in cs?.items ?? [] { rc[c.card_id] = c }
            }

            events = evs
            participations = parts
            rewardCards = rc
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func join(_ e: CommunityEvent) async {
        guard busy == nil else { return }
        busy = e.id
        defer { busy = nil }
        do {
            let d = try await Engines.community("join", ["event_id": e.id])
            let o = Engines.json(d)
            if let p = rvDecode(EventParticipation.self, o["participation"]) {
                participations[e.id] = p
                app.show("Joined event!")
            } else {
                app.show("Could not join event", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not join event", error)
        }
    }

    private func updateProgress(_ e: CommunityEvent) async {
        guard busy == nil else { return }
        busy = e.id
        defer { busy = nil }
        do {
            let d = try await Engines.community("progress", ["event_id": e.id])
            let o = Engines.json(d)
            if let p = rvDecode(EventParticipation.self, o["participation"]) {
                participations[e.id] = p
                app.show("Progress: \(p.progress)/\(e.requirement_value)")
            } else {
                app.show("Could not update progress", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not update progress", error)
        }
    }

    private func claimLegacy(_ e: CommunityEvent) async {
        guard busy == nil else { return }
        busy = e.id
        defer { busy = nil }
        do {
            let d = try await Engines.community("claim", ["event_id": e.id])
            let o = Engines.json(d)
            if (o["ok"] as? Bool) == true {
                app.show("Reward claimed!", "A new card was added to your collection")
                app.celebrate()
                app.dataChanged()
                await load()
            } else {
                app.show("Could not claim reward", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not claim reward", error)
        }
    }

    private func toggleLeaderboard(_ e: CommunityEvent) {
        if openLeaderboards.contains(e.id) {
            openLeaderboards.remove(e.id)
            return
        }
        openLeaderboards.insert(e.id)
        let eid = e.id
        Task {
            let rows = (try? await Engines.leaderboard(eid)) ?? []
            leaderboards[eid] = Array(rows.prefix(8))
        }
    }
}

// MARK: - Event detail (eventEngine + grand events)

struct EventDetailView: View {
    let eventId: String
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var state: EventEngineState?
    @State private var grand: GrandEventState?
    @State private var grandCards: [TradingCard] = []
    @State private var grandOwned: Set<String> = []
    @State private var loading = true
    @State private var errorText: String?
    @State private var busy: String?

    private var navTitle: String {
        nonEmpty(state?.event?.title) ?? nonEmpty(grand?.event?.title) ?? "Event"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle(navTitle)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    @ViewBuilder private var content: some View {
        if loading && state == nil && grand == nil {
            LoadingView()
        } else if let g = grand {
            grandContent(g)
        } else if let s = state, let e = s.event {
            engineContent(s, e)
        } else {
            VStack(spacing: 8) {
                RVErrorState(message: errorText ?? "This event could not be found.") {
                    Task { await load() }
                }
                Button("Back") { dismiss() }.buttonStyle(OutlineButtonStyle())
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Engine variant

    private func engineContent(_ s: EventEngineState, _ e: CommunityEvent) -> some View {
        let accent = Color(hex: e.accent)
        return VStack(alignment: .leading, spacing: 18) {
            engineHero(s, e, accent: accent)
            engineNotice(s, e)
            engineQuests(s, accent: accent)
            engineProgress(s, accent: accent)
            engineRewards(s, accent: accent)
            engineAbout(e)
        }
    }

    private func engineStatusLabel(_ status: String) -> String {
        switch status {
        case "active": return "● Live now"
        case "scheduled": return "Starts soon"
        case "ended": return "Event ended"
        case "draft": return "Draft — only admins see this"
        case "archived": return "Archived"
        default: return status.capitalized
        }
    }

    private func engineHero(_ s: EventEngineState, _ e: CommunityEvent, accent: Color) -> some View {
        let status = s.status ?? ""
        let deco = e.decoration ?? ""
        let snow = (deco == "snow" || deco == "lights") && status != "ended"
        let canJoin = (e.requires_join ?? false) && s.participation == nil && status == "active"
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(engineStatusLabel(status)).font(.caption.weight(.black)).foregroundStyle(accent)
                if !e.typeLabel.isEmpty {
                    Text(e.typeLabel.uppercased()).font(.caption2.weight(.black)).tracking(1).foregroundStyle(.white.opacity(0.7))
                }
            }
            Text(e.displayTitle.isEmpty ? "Event" : e.displayTitle)
                .font(.title2.weight(.black)).foregroundStyle(.white).multilineTextAlignment(.leading)
            if let sub = nonEmpty(e.subtitle) {
                Text(sub).font(.subheadline.weight(.bold)).foregroundStyle(.white.opacity(0.85))
            }
            if let sd = nonEmpty(e.short_description) {
                Text(sd).font(.caption).foregroundStyle(.white.opacity(0.75))
            }
            Text(rvDateRange(e)).font(.caption2.weight(.bold)).foregroundStyle(.white.opacity(0.65))
            if let claimEnd = nonEmpty(e.reward_claim_end) {
                Text("Rewards claimable until \(claimEnd)").font(.caption2).foregroundStyle(.white.opacity(0.6))
            }
            if status == "active" {
                Text("ENDS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: ServerDate.parse(s.end), compact: true).foregroundStyle(.white)
            } else if status == "scheduled" {
                Text("STARTS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: ServerDate.parse(s.start), compact: true).foregroundStyle(.white)
            }
            if canJoin {
                Button {
                    Task { await join() }
                } label: {
                    Text(busy == "join" ? "Joining…" : "Join this event")
                }
                .buttonStyle(PrimaryButtonStyle(color: accent))
                .disabled(busy != nil)
                .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 240, alignment: .bottomLeading)
        .background {
            RVHeroBackground(image: e.heroImage, accent: accent, snow: snow, lights: deco == "lights")
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.5)))
    }

    @ViewBuilder private func engineNotice(_ s: EventEngineState, _ e: CommunityEvent) -> some View {
        if s.status == "scheduled" {
            let when = [e.start_date, e.start_time].compactMap { nonEmpty($0) }.joined(separator: " ")
            let tz = nonEmpty(e.timezone) ?? "Europe/Amsterdam"
            RVNotice(text: "This event has not started yet. Come back on \(when) (\(tz)) — your quests start counting the moment you start the event.",
                     color: Theme.sky, icon: "clock.fill")
        } else if s.status == "ended" {
            let claimEnd = nonEmpty(e.reward_claim_end) ?? (e.end_date ?? "")
            RVNotice(text: "This event has ended. Everything you earned stays on your profile, and you can still claim rewards until \(claimEnd).",
                     color: Theme.amber, icon: "flag.checkered")
        }
    }

    private func engineQuests(_ s: EventEngineState, accent: Color) -> some View {
        let quests = s.quests ?? []
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                RVSectionTitle(title: "Event quests", icon: "target", color: accent)
                Spacer()
                Text("\(s.done_count)/\(quests.count) quests done").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Button {
                    Task { await load() }
                } label: {
                    Image(systemName: "arrow.clockwise").font(.caption.weight(.bold))
                }
                .foregroundStyle(accent)
            }
            if quests.isEmpty {
                Text("This event has no quests yet.").font(.subheadline).foregroundStyle(Theme.muted)
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
            } else {
                ForEach(quests) { q in engineQuestRow(q, s, accent: accent) }
            }
        }
    }

    private func engineQuestRow(_ q: EventQuestDef, _ s: EventEngineState, accent: Color) -> some View {
        let done = q.done ?? false
        let repeatable = q.repeatable ?? false
        let target = max(q.target, 1)
        let shown = min(q.count, target)
        let repeatSuffix: String = repeatable ? " · repeatable" : ""
        let kindText: String = q.kindLabel.isEmpty ? (repeatable ? "Repeatable" : "") : q.kindLabel + repeatSuffix
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Text(nonEmpty(q.icon) ?? "⭐")
                    .font(.system(size: 24))
                    .frame(width: 44, height: 44)
                    .background(accent.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 3) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(nonEmpty(q.label) ?? q.key).font(.subheadline.weight(.black))
                        Spacer(minLength: 4)
                        Text("+\(q.xp) XP").font(.caption.weight(.black)).foregroundStyle(accent)
                    }
                    if let d = nonEmpty(q.description) {
                        Text(d).font(.caption).foregroundStyle(Theme.muted)
                    }
                    if !kindText.isEmpty {
                        Text(kindText).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted.opacity(0.8))
                    }
                }
            }
            HStack(spacing: 8) {
                ProgressBar(value: Double(shown) / Double(target), height: 6, color: accent)
                Text("\(shown)/\(target)").font(.caption2.weight(.black)).foregroundStyle(Theme.muted)
            }
            engineQuestChips(q)
            engineQuestAction(q, s, accent: accent)
        }
        .padding(14)
        .cardStyle(highlighted: done)
    }

    @ViewBuilder private func engineQuestChips(_ q: EventQuestDef) -> some View {
        let hasCard = nonEmpty(q.reward_card_id) != nil
        let hasBadge = nonEmpty(q.badge_id) != nil
        let claimedOnce = (q.claimed ?? false) && !(q.repeatable ?? false)
        if hasCard || hasBadge || claimedOnce {
            HStack(spacing: 6) {
                if hasCard { Badge(text: "Trading card", icon: "rectangle.stack.fill", fg: Theme.sky, bg: Theme.sky.opacity(0.15)) }
                if hasBadge { Badge(text: "Badge", icon: "rosette", fg: Theme.amber, bg: Theme.amber.opacity(0.15)) }
                if claimedOnce { Badge(text: "Reward claimed", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.15)) }
            }
        }
    }

    @ViewBuilder private func engineQuestAction(_ q: EventQuestDef, _ s: EventEngineState, accent: Color) -> some View {
        let done = q.done ?? false
        let claimed = q.claimed ?? false
        let repeatable = q.repeatable ?? false
        if done && (!claimed || repeatable) {
            if s.can_claim ?? false {
                Button {
                    Task { await claimQuest(q) }
                } label: {
                    Text(busy == q.key ? "Claiming…" : (claimed ? "Claim again" : "Claim reward")).frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle(color: accent))
                .disabled(busy != nil)
            } else {
                Text("Reward claim period closed").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
            }
        } else if !done {
            Text("Not completed yet").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
        }
    }

    private func engineProgress(_ s: EventEngineState, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            RVSectionTitle(title: "Your progress", icon: "chart.bar.fill", color: accent)
            HStack {
                Text("Earned XP").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Spacer()
                Text("\(s.earned) / \(s.max_xp)").font(.caption.weight(.black))
            }
            ProgressBar(value: Double(s.earned) / Double(max(s.max_xp, 1)), height: 8, color: accent)
            Text(s.participation != nil
                 ? "Points from completed quests: \(s.points)"
                 : "Your progress starts counting the moment you start this event.")
                .font(.caption).foregroundStyle(Theme.muted)
            if let c = s.community {
                communityBlock(c, accent: accent)
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func communityBlock(_ c: CommunityProgress, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Community goal").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Spacer()
                Text("\(c.total)/\(c.goal)").font(.caption.weight(.black))
            }
            ProgressBar(value: Double(c.total) / Double(max(c.goal, 1)), height: 8, color: accent)
            Text("\(c.participants) participant\(c.participants == 1 ? "" : "s") · you contributed \(c.mine)")
                .font(.caption2).foregroundStyle(Theme.muted)
        }
        .padding(.top, 6)
    }

    private func engineRewards(_ s: EventEngineState, accent: Color) -> some View {
        let cards = s.rewards?.cards ?? []
        let owned = Set(s.owned_cards ?? [])
        let completed = s.quests_completed ?? false
        let rewardClaimed = s.participation?.reward_claimed ?? false
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Rewards", icon: "gift.fill", color: accent)
            if cards.isEmpty {
                Text("No trading card rewards for this event.").font(.caption).foregroundStyle(Theme.muted)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(cards) { c in
                            TradingCardView(card: c, owned: owned.contains(c.card_id), size: .sm)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            if let badge = nonEmpty(s.rewards?.badge_name) {
                Label("Badge: \(badge)", systemImage: "rosette").font(.caption.weight(.bold)).foregroundStyle(Theme.amber)
            }
            if completed {
                if rewardClaimed {
                    Badge(text: "Completion reward claimed", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                } else {
                    Button {
                        Task { await claimEvent() }
                    } label: {
                        Text(busy == "claim_event" ? "Claiming…" : "Claim completion reward").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: accent))
                    .disabled(busy != nil)
                }
            } else {
                Text("Complete every quest to unlock the event reward.").font(.caption).foregroundStyle(Theme.muted)
            }
        }
        .padding(16)
        .cardStyle()
    }

    @ViewBuilder private func engineAbout(_ e: CommunityEvent) -> some View {
        let long = nonEmpty(e.long_description)
        let source = nonEmpty(e.image_source)
        if long != nil || source != nil {
            VStack(alignment: .leading, spacing: 10) {
                RVSectionTitle(title: "About this event", icon: "info.circle")
                if let long {
                    Text(LocalizedStringKey(long)).font(.subheadline).foregroundStyle(Theme.foreground)
                }
                if let source {
                    Text("Image: \(source)").font(.caption2).foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .cardStyle()
        }
    }

    // MARK: Grand variant (communityEngine)

    private func grandContent(_ g: GrandEventState) -> some View {
        let accent = Color(hex: g.event?.accent ?? "#FF7A00")
        return VStack(alignment: .leading, spacing: 18) {
            grandHero(g, accent: accent)
            if let c = g.community { grandCommunity(c, accent: accent) }
            grandQuests(g, accent: accent)
            grandRewards(g, accent: accent)
        }
    }

    private func grandHero(_ g: GrandEventState, accent: Color) -> some View {
        let status = g.status ?? ""
        let label = status == "live" ? "● Live now" : (status == "ended" ? "Event ended" : "Coming soon")
        let e = g.event
        let deco = e?.decoration ?? ""
        let snow = (deco == "snow" || deco == "lights") && status != "ended"
        return VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.caption.weight(.black)).foregroundStyle(accent)
            Text(nonEmpty(e?.displayTitle) ?? "Grand Event").font(.title2.weight(.black)).foregroundStyle(.white)
            if let sub = nonEmpty(e?.subtitle) ?? nonEmpty(e?.short_description) {
                Text(sub).font(.subheadline).foregroundStyle(.white.opacity(0.8))
            }
            if let e {
                Text(rvDateRange(e)).font(.caption2.weight(.bold)).foregroundStyle(.white.opacity(0.65))
            }
            if status == "live" {
                Text("ENDS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: ServerDate.parse(g.end), compact: true).foregroundStyle(.white)
            } else if status != "ended" {
                Text("STARTS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: ServerDate.parse(g.start), compact: true).foregroundStyle(.white)
            }
            if let q = g.quests {
                Text("Your points: \(q.points)").font(.subheadline.weight(.black)).foregroundStyle(accent).padding(.top, 2)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 230, alignment: .bottomLeading)
        .background {
            RVHeroBackground(image: e?.heroImage, accent: accent, snow: snow, lights: deco == "lights")
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.5)))
    }

    private func grandCommunity(_ c: CommunityProgress, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            RVSectionTitle(title: "Community challenge", icon: "person.3.fill", color: accent)
            Text("Log reading progress together. Each unique volume counts once per reader.")
                .font(.caption).foregroundStyle(Theme.muted)
            HStack {
                Text("\(c.total)/\(c.goal) volumes").font(.subheadline.weight(.black))
                Spacer()
                Text("\(c.participants) participant\(c.participants == 1 ? "" : "s")").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
            }
            ProgressBar(value: Double(c.total) / Double(max(c.goal, 1)), height: 10, color: accent)
            Text("Your contribution: \(c.mine)").font(.caption.weight(.bold)).foregroundStyle(accent)
        }
        .padding(16)
        .cardStyle()
    }

    private func grandQuests(_ g: GrandEventState, accent: Color) -> some View {
        let quests = g.quests?.quests ?? []
        return VStack(alignment: .leading, spacing: 10) {
            RVSectionTitle(title: "Quests", icon: "target", color: accent)
            if quests.isEmpty {
                Text("Quests unlock when the event goes live.").font(.caption).foregroundStyle(Theme.muted)
            } else {
                ForEach(quests) { q in grandQuestRow(q, accent: accent) }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func grandQuestRow(_ q: EventQuestDef, accent: Color) -> some View {
        let done = q.done ?? false
        let target = max(q.target, 1)
        return HStack(spacing: 10) {
            Image(systemName: done ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(done ? Theme.emerald : Theme.muted)
            VStack(alignment: .leading, spacing: 1) {
                Text(nonEmpty(q.label) ?? q.key).font(.subheadline.weight(.bold))
                if let d = nonEmpty(q.description) {
                    Text(d).font(.caption2).foregroundStyle(Theme.muted).lineLimit(2)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text("\(min(q.count, target))/\(target)").font(.caption.weight(.black))
                Text("+\(q.points)").font(.caption2.weight(.black)).foregroundStyle(accent)
            }
        }
        .padding(10)
        .background(done ? Theme.emerald.opacity(0.08) : Theme.secondary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
    }

    private func grandRewards(_ g: GrandEventState, accent: Color) -> some View {
        let ownedCount = grandCards.filter { grandOwned.contains($0.card_id) }.count
        let canClaim = g.status != "upcoming" && g.quests != nil
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                RVSectionTitle(title: "Exclusive rewards", icon: "gift.fill", color: accent)
                Spacer()
                if !grandCards.isEmpty {
                    Text("(\(ownedCount)/\(grandCards.count) collected)").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                }
            }
            if !grandCards.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(grandCards) { c in
                            TradingCardView(card: c, owned: grandOwned.contains(c.card_id), size: .sm)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            if let badge = nonEmpty(g.event?.badge_name) {
                Label("Badge: \(badge)", systemImage: "rosette").font(.caption.weight(.bold)).foregroundStyle(Theme.amber)
            }
            if canClaim {
                Button {
                    Task { await grandClaim() }
                } label: {
                    Text(busy == "grand_claim" ? "Claiming…" : "Claim rewards").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle(color: accent))
                .disabled(busy != nil)
            }
        }
        .padding(16)
        .cardStyle()
    }

    // MARK: Data

    private func load() async {
        do {
            let s = try await Engines.eventState(eventId, visit: true)
            if s.event?.type == "grand_event" {
                await loadGrand()
            } else if s.event == nil {
                state = nil
                errorText = nonEmpty(s.error) ?? "This event could not be found."
            } else {
                state = s
                errorText = nil
            }
        } catch {
            if !(error is CancellationError) {
                errorText = error.localizedDescription
            }
        }
        loading = false
    }

    private func loadGrand() async {
        do {
            let d = try await Engines.community("grand_state", ["event_id": eventId, "visit": true])
            let g = try Base44Client.shared.decode(GrandEventState.self, d)
            if g.event == nil, let err = nonEmpty(g.error) {
                errorText = err
                return
            }
            grand = g
            errorText = nil
            var ids: [String] = []
            var candidates: [String] = g.event?.extra_card_ids ?? []
            if let rewardId = nonEmpty(g.event?.reward_card_id) { candidates.append(rewardId) }
            for id in candidates {
                if !id.isEmpty && !ids.contains(id) { ids.append(id) }
            }
            if !ids.isEmpty {
                let cards = (try? await Engines.cards(ids: ids)) ?? []
                let ownedQuery: [String: Any] = ["card_id": ["$in": ids]]
                let mine = (try? await Engines.myCards(limit: 100, query: ownedQuery)) ?? []
                grandCards = ids.compactMap { id in cards.first(where: { $0.card_id == id }) }
                grandOwned = Set(mine.map { $0.card_id })
            } else {
                grandCards = []
                grandOwned = []
            }
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
    }

    private func join() async {
        guard busy == nil else { return }
        busy = "join"
        defer { busy = nil }
        do {
            _ = try await Engines.eventAction("join", eventId: eventId)
            app.show("Joined event!")
            await load()
        } catch {
            app.fail("Could not join event", error)
        }
    }

    private func claimQuest(_ q: EventQuestDef) async {
        guard busy == nil else { return }
        busy = q.key
        defer { busy = nil }
        do {
            let o = try await Engines.eventAction("claim_quest", eventId: eventId, questKey: q.key)
            let xp = (o["xp"] as? NSNumber)?.intValue ?? q.xp
            app.show(xp > 0 ? "+\(xp) XP claimed" : "Reward claimed")
            app.celebrate()
            app.dataChanged()
            await load()
        } catch {
            app.fail("Could not claim reward", error)
        }
    }

    private func claimEvent() async {
        guard busy == nil else { return }
        busy = "claim_event"
        defer { busy = nil }
        do {
            _ = try await Engines.eventAction("claim_event", eventId: eventId)
            app.show("Reward claimed!", "Your event rewards were added to your profile.")
            app.celebrate()
            app.dataChanged()
            await load()
        } catch {
            app.fail("Could not claim reward", error)
        }
    }

    private func grandClaim() async {
        guard busy == nil else { return }
        busy = "grand_claim"
        defer { busy = nil }
        do {
            let d = try await Engines.community("grand_claim", ["event_id": eventId])
            let r = try? Base44Client.shared.decode(GrandEventState.self, d)
            let granted = r?.granted ?? []
            if !granted.isEmpty {
                app.show("Rewards claimed!", "\(granted.count) card\(granted.count == 1 ? "" : "s") added to your collection")
                app.celebrate()
                app.dataChanged()
                await loadGrand()
            } else {
                let fallback = (Engines.json(d)["error"] as? String) ?? "Nothing to claim yet."
                app.show("Cannot claim", nonEmpty(r?.error) ?? fallback, error: true)
            }
        } catch {
            app.fail("Cannot claim", error)
        }
    }
}

// MARK: - Special events (Christmas & Boruto) shared bits

fileprivate struct RVSpecialQuest {
    let key: String
    let title: String
    let desc: String
    let points: Int
    let goal: Int
}

fileprivate let rvChristmasQuests: [RVSpecialQuest] = [
    RVSpecialQuest(key: "c1_reader", title: "Winter Reader", desc: "Register reading progress for 2 different volumes.", points: 75, goal: 2),
    RVSpecialQuest(key: "c2_collector", title: "Christmas Collector", desc: "Add 3 different volumes to your collection.", points: 100, goal: 3),
    RVSpecialQuest(key: "c3_discovery", title: "Holiday Discovery", desc: "Add a manga that was not in your collection yet.", points: 75, goal: 1),
    RVSpecialQuest(key: "c4_spirit", title: "Share the Christmas Spirit", desc: "Publish a manga recommendation or review (min. 20 characters).", points: 100, goal: 1),
    RVSpecialQuest(key: "c5_champion", title: "Christmas Champion", desc: "Complete 3 of the quests above. Unlocks the exclusive card + badge.", points: 200, goal: 3)
]

fileprivate let rvBorutoQuests: [RVSpecialQuest] = [
    RVSpecialQuest(key: "q1_visit", title: "The Shinobi Returns", desc: "Visit the Boruto Volume 7 event page.", points: 50, goal: 1),
    RVSpecialQuest(key: "q2_collect", title: "Boruto Collector", desc: "Add Boruto: Two Blue Vortex Volume 7 (English) to your collection.", points: 100, goal: 1),
    RVSpecialQuest(key: "q3_progress", title: "New Volume, New Adventure", desc: "Register reading progress for Volume 7.", points: 100, goal: 1),
    RVSpecialQuest(key: "q4_review", title: "Share the Hype", desc: "Publish a recommendation or review about Boruto: Two Blue Vortex.", points: 75, goal: 1),
    RVSpecialQuest(key: "q5_champion", title: "Release Week Champion", desc: "Complete three of the quests above.", points: 150, goal: 3)
]

fileprivate struct RVSpecialQuestRow<Trailing: View>: View {
    let quest: RVSpecialQuest
    let count: Int
    let done: Bool
    let accent: Color
    @ViewBuilder var trailing: Trailing

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: done ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(done ? Theme.emerald : Theme.muted)
                VStack(alignment: .leading, spacing: 3) {
                    Text(quest.title).font(.subheadline.weight(.black))
                    Text(quest.desc).font(.caption).foregroundStyle(Theme.muted)
                    Text("+\(quest.points) pts · \(min(count, quest.goal))/\(quest.goal)")
                        .font(.caption2.weight(.black)).foregroundStyle(accent)
                }
                Spacer(minLength: 4)
                if done {
                    Label("Done", systemImage: "checkmark").font(.caption.weight(.black)).foregroundStyle(Theme.emerald)
                }
            }
            if !done {
                trailing
            }
        }
        .padding(14)
        .cardStyle(highlighted: done)
    }
}

fileprivate struct RVTextEditorBox: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        ZStack(alignment: .topLeading) {
            TextEditor(text: $text)
                .scrollContentBackground(.hidden)
                .frame(minHeight: 90)
                .padding(6)
            if text.isEmpty {
                Text(placeholder).font(.subheadline).foregroundStyle(Theme.muted)
                    .padding(.horizontal, 11).padding(.vertical, 14)
                    .allowsHitTesting(false)
            }
        }
        .background(Theme.secondary, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
    }
}

/// Smaller in-card action button used by the special event quests.
fileprivate struct RVSmallButton: View {
    let title: String
    var icon: String? = nil
    var color: Color = Theme.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let icon { Image(systemName: icon) }
                Text(title)
            }
            .font(.caption.weight(.bold))
            .padding(.horizontal, 12).padding(.vertical, 8)
            .foregroundStyle(.white)
            .background(color, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Christmas event

struct ChristmasEventView: View {
    @Environment(AppState.self) private var app
    @State private var event: CommunityEvent?
    @State private var st: SpecialEventState?
    @State private var card: TradingCard?
    @State private var ownsCard = false
    @State private var loading = true
    @State private var noEvent = false
    @State private var errorText: String?
    @State private var busy: String?
    @State private var recommendation = ""

    private let accent = Theme.red

    private var status: String { st?.status ?? "upcoming" }
    private var isLive: Bool { status == "live" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if loading && event == nil && !noEvent {
                    LoadingView()
                } else if noEvent {
                    EmptyState(icon: "snowflake", text: "No Christmas event configured.")
                } else if let e = event {
                    hero(e)
                    notice(e)
                    questsSection
                    rewardSection
                    Text("Photo: Pexels (free to use under the Pexels License).").font(.caption2).foregroundStyle(Theme.muted)
                } else {
                    RVErrorState(message: errorText ?? "Could not load the Christmas event.") {
                        Task { await load() }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Christmas Event")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func hero(_ e: CommunityEvent) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(isLive ? "● Live now" : (status == "ended" ? "Event ended" : "Coming soon"))
                .font(.caption.weight(.black)).foregroundStyle(Theme.emerald)
            Text("🎄 CHRISTMAS READING FESTIVAL").font(.title2.weight(.black)).foregroundStyle(.white)
            Text("Celebrate the holidays, discover manga and earn exclusive rewards!")
                .font(.subheadline).foregroundStyle(.white.opacity(0.85))
            Text("\(e.start_date ?? "") → \(e.end_date ?? "")").font(.caption2.weight(.bold)).foregroundStyle(.white.opacity(0.65))
            if isLive {
                Text("ENDS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: RVDay.endOfDay(e.end_date), compact: true).foregroundStyle(.white)
            } else if status != "ended" {
                Text("STARTS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                CountdownView(target: RVDay.parse(e.start_date), compact: true).foregroundStyle(.white)
            }
        }
        .padding(16)
        .padding(.top, 20)
        .frame(maxWidth: .infinity, minHeight: 240, alignment: .bottomLeading)
        .background {
            RVHeroBackground(image: nonEmpty(e.banner_url) ?? e.heroImage, accent: accent, snow: true, lights: true)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.5)))
    }

    @ViewBuilder private func notice(_ e: CommunityEvent) -> some View {
        if !isLive {
            if status == "ended" {
                RVNotice(text: "The festival has ended. Earned cards, badges and points stay on your profile forever.",
                         color: Theme.amber, icon: "flag.checkered")
            } else {
                RVNotice(text: "Quests open on \(e.start_date ?? "the start date"). Activity from that day on counts!",
                         color: Theme.sky, icon: "clock.fill")
            }
        }
    }

    private var questsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                RVSectionTitle(title: "Festival quests", icon: "snowflake", color: accent)
                Spacer()
                Text("\(st?.quests?.points ?? 0) event points").font(.caption.weight(.black)).foregroundStyle(Theme.emerald)
                Button {
                    Task { await load() }
                } label: {
                    Image(systemName: "arrow.clockwise").font(.caption.weight(.bold))
                }
                .foregroundStyle(accent)
            }
            ForEach(rvChristmasQuests, id: \.key) { q in
                RVSpecialQuestRow(quest: q, count: st?.count(q.key) ?? 0, done: st?.isDone(q.key) ?? false, accent: accent) {
                    questAction(q.key)
                }
            }
        }
    }

    @ViewBuilder private func questAction(_ key: String) -> some View {
        if key == "c1_reader" {
            Button { app.selectTab(.collection) } label: {
                Label("Go to Collection", systemImage: "books.vertical").font(.caption.weight(.bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(Theme.primary, in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        } else if key == "c2_collector" {
            NavigationLink(value: Route.discover) {
                Label("Discover", systemImage: "safari").font(.caption.weight(.bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(accent, in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        } else if key == "c3_discovery" {
            NavigationLink(value: Route.random) {
                Label("Find one", systemImage: "dice").font(.caption.weight(.bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(accent, in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        } else if key == "c4_spirit" {
            if isLive {
                VStack(alignment: .leading, spacing: 8) {
                    RVTextEditorBox(placeholder: "Which manga would you recommend this Christmas, and why?", text: $recommendation)
                    RVSmallButton(title: busy == "c4" ? "Publishing…" : "Publish", icon: "paperplane.fill", color: accent) {
                        Task { await publishRecommendation() }
                    }
                    .disabled(busy != nil)
                }
            }
        } else {
            EmptyView()
        }
    }

    private var rewardSection: some View {
        let champion = st?.isDone("c5_champion") ?? false
        let claimed = st?.participation?.reward_claimed ?? false
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Exclusive reward", icon: "gift.fill", color: accent)
            HStack(alignment: .top, spacing: 14) {
                if let card {
                    TradingCardView(card: card, owned: ownsCard, size: .lg)
                }
                VStack(alignment: .leading, spacing: 10) {
                    Text("Become Christmas Champion to earn the epic Christmas Manga Spirit card and the Christmas Champion badge.")
                        .font(.subheadline).foregroundStyle(Theme.muted)
                    if claimed {
                        Badge(text: "Reward earned", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                    } else if champion {
                        Button {
                            Task { await claim() }
                        } label: {
                            Text(busy == "claim" ? "Claiming…" : "Claim Reward").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(PrimaryButtonStyle(color: accent))
                        .disabled(busy != nil)
                    } else {
                        Text("Complete 3 quests to unlock").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                    }
                    NavigationLink(value: Route.cards) {
                        Text("View my cards →").font(.subheadline.weight(.bold)).foregroundStyle(accent)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func load() async {
        do {
            let page = try await Base44Client.shared.list("CommunityEvent", CommunityEvent.self,
                                                          query: ["type": "christmas_event"], sort: "-start_date", limit: 1)
            guard let e = page.items.first else {
                event = nil
                noEvent = true
                loading = false
                return
            }
            noEvent = false
            let d = try await Engines.community("christmas_state", ["event_id": e.id])
            let state = try? Base44Client.shared.decode(SpecialEventState.self, d)
            var rewardCard: TradingCard?
            var owns = false
            if let rid = nonEmpty(e.reward_card_id) {
                let cardPage = try? await Base44Client.shared.list("TradingCard", TradingCard.self, query: ["card_id": rid], limit: 1)
                rewardCard = cardPage?.items.first
                let mine = (try? await Engines.myCards(limit: 5, query: ["card_id": rid])) ?? []
                owns = !mine.isEmpty
            }
            event = e
            st = state
            card = rewardCard
            ownsCard = owns
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    private func publishRecommendation() async {
        guard isLive else { return }
        let text = recommendation.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 20 else {
            app.show("Write at least 20 characters", error: true)
            return
        }
        guard busy == nil else { return }
        busy = "c4"
        defer { busy = nil }
        do {
            let payload: [String: Any] = ["entry_type": "note", "title": "Christmas recommendation", "content": text]
            _ = try await Base44Client.shared.create("JournalEntry", payload, RVIgnored.self)
            recommendation = ""
            app.show("Recommendation published!")
            app.dataChanged()
            await load()
        } catch {
            app.fail("Could not publish", error)
        }
    }

    private func claim() async {
        guard let e = event, busy == nil else { return }
        busy = "claim"
        defer { busy = nil }
        do {
            let d = try await Engines.community("claim", ["event_id": e.id])
            let o = Engines.json(d)
            if (o["ok"] as? Bool) == true {
                app.show("Christmas Manga Spirit claimed!", "Card and Christmas Champion badge added permanently.")
                app.celebrate()
                app.dataChanged()
                await load()
            } else {
                app.show("Could not claim reward", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not claim reward", error)
        }
    }
}

// MARK: - Boruto special release event

struct BorutoEventView: View {
    @Environment(AppState.self) private var app
    @State private var event: CommunityEvent?
    @State private var st: SpecialEventState?
    @State private var card: TradingCard?
    @State private var ownsCard = false
    @State private var leaders: [LeaderboardRow]?
    @State private var loading = true
    @State private var noEvent = false
    @State private var errorText: String?
    @State private var busy: String?
    @State private var reviewOpen = false
    @State private var reviewText = ""

    private static let mangaId = "6acd0e7d0901a2ed093a067a"
    private static let cover = "https://uploads.mangadex.org/covers/0b094aab-0cfb-4837-a49b-7267bdb86bec/baf472db-4658-46fb-8130-d4abdd43f171.jpg.512.jpg"
    private static let mangaTitle = "Boruto - Two Blue Vortex"
    private static let totalPoints = 475

    private var accent: Color { Color(hex: event?.accent ?? "#FF7A00") }
    private var status: String { st?.status ?? "upcoming" }
    private var isLive: Bool { status == "live" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if loading && event == nil && !noEvent {
                    LoadingView()
                } else if noEvent {
                    EmptyState(icon: "leaf", text: "No special release event is configured yet.")
                } else if let e = event {
                    hero(e)
                    notice
                    progressCard
                    questsSection
                    rewardSection
                    leaderboardSection
                } else {
                    RVErrorState(message: errorText ?? "Could not load the release event.") {
                        Task { await load() }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Special Release")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func hero(_ e: CommunityEvent) -> some View {
        let pillText = isLive ? "Live" : (status == "ended" ? "Event Ended" : "Coming Soon")
        let pillColor = isLive ? Theme.emerald : (status == "ended" ? Theme.muted : Theme.sky)
        let start = e.start_date ?? ""
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                RemoteImage(url: Self.cover)
                    .frame(width: 100, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.15)))
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Text("Special Release Event".uppercased()).font(.caption2.weight(.black)).tracking(1).foregroundStyle(accent)
                        Badge(text: pillText, fg: .white, bg: pillColor)
                    }
                    Text("🍃 Boruto Volume 7 Release Celebration").font(.title3.weight(.black)).foregroundStyle(.white)
                    Text("Boruto: Two Blue Vortex — Volume 7 (English edition). Celebrate the release, complete quests, and earn an exclusive legendary trading card.")
                        .font(.caption).foregroundStyle(.white.opacity(0.75))
                }
            }
            if status != "ended" && !start.isEmpty {
                CountdownView(target: ServerDate.parse("\(start)T00:00:00+01:00"), compact: true).foregroundStyle(.white)
            }
            Text("Planned release \(start). This date may change — admins can update it.")
                .font(.caption2).foregroundStyle(.white.opacity(0.6))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RVHeroBackground(image: e.heroImage ?? Self.cover, accent: accent)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.5)))
    }

    @ViewBuilder private var notice: some View {
        if !isLive {
            if status == "ended" {
                RVNotice(text: "This event has ended. Earned cards, badges and points are kept on your profile.",
                         color: Theme.amber, icon: "flag.checkered")
            } else {
                RVNotice(text: "Quests and rewards activate on the release day. Check back when Volume 7 drops!",
                         color: Theme.sky, icon: "clock.fill")
            }
        }
    }

    private var progressCard: some View {
        let points = st?.quests?.points ?? 0
        let baseKeys = ["q1_visit", "q2_collect", "q3_progress", "q4_review"]
        let baseDone = baseKeys.filter { st?.isDone($0) ?? false }.count
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                RVSectionTitle(title: "Your progress", icon: "chart.bar.fill", color: accent)
                Spacer()
                Text("\(points) pts").font(.headline.weight(.black)).foregroundStyle(accent)
            }
            HStack {
                Text("Quests completed").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Spacer()
                Text("\(baseDone)/4 base quests").font(.caption.weight(.black))
            }
            ProgressBar(value: Double(baseDone) / 4.0, height: 8, color: accent)
            HStack {
                Text("Points to champion").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                Spacer()
                Text("\(points)/\(Self.totalPoints)").font(.caption.weight(.black))
            }
            ProgressBar(value: Double(points) / Double(Self.totalPoints), height: 8, color: Theme.amber)
        }
        .padding(16)
        .cardStyle()
    }

    private var questsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                RVSectionTitle(title: "Release quests", icon: "target", color: accent)
                Spacer()
                Button {
                    Task { await load() }
                } label: {
                    Image(systemName: "arrow.clockwise").font(.caption.weight(.bold))
                }
                .foregroundStyle(accent)
            }
            ForEach(rvBorutoQuests, id: \.key) { q in
                RVSpecialQuestRow(quest: q, count: st?.count(q.key) ?? 0, done: st?.isDone(q.key) ?? false, accent: accent) {
                    questAction(q.key)
                }
            }
        }
    }

    @ViewBuilder private func questAction(_ key: String) -> some View {
        let working = busy == key
        if key == "q1_visit" {
            RVSmallButton(title: working ? "Working…" : "Mark Visit", icon: "eye", color: accent) { markVisit() }
                .disabled(busy != nil)
        } else if key == "q2_collect" {
            RVSmallButton(title: working ? "Adding…" : "Add Vol. 7", icon: "plus", color: accent) { addVolume() }
                .disabled(busy != nil)
        } else if key == "q3_progress" {
            RVSmallButton(title: working ? "Logging…" : "Log Progress", icon: "book", color: accent) { logProgress() }
                .disabled(busy != nil)
        } else if key == "q4_review" {
            VStack(alignment: .leading, spacing: 8) {
                RVSmallButton(title: reviewOpen ? "Cancel" : "Review", icon: "square.and.pencil", color: reviewOpen ? Theme.secondary : accent) {
                    reviewOpen.toggle()
                }
                if reviewOpen {
                    RVTextEditorBox(placeholder: "What do you think of Boruto: Two Blue Vortex?", text: $reviewText)
                    RVSmallButton(title: working ? "Publishing…" : "Publish", icon: "paperplane.fill", color: accent) { publishReview() }
                        .disabled(busy != nil)
                }
            }
        } else {
            Text("Complete 3 quests").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
        }
    }

    private var rewardSection: some View {
        let champion = st?.isDone("q5_champion") ?? false
        let claimed = (st?.participation?.reward_claimed ?? false) || ownsCard
        return VStack(alignment: .leading, spacing: 12) {
            RVSectionTitle(title: "Exclusive reward", icon: "gift.fill", color: accent)
            HStack(alignment: .top, spacing: 14) {
                if let card {
                    TradingCardView(card: card, owned: ownsCard, size: .md)
                }
                VStack(alignment: .leading, spacing: 10) {
                    Text("Earn the Boruto Volume 7 — Release Edition legendary card by completing the Release Week Champion quest.")
                        .font(.subheadline).foregroundStyle(Theme.muted)
                    if claimed {
                        Badge(text: "Exclusive card earned", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                    } else if champion {
                        Button {
                            Task { await claim() }
                        } label: {
                            Text(busy == "claim" ? "Claiming…" : "Claim Exclusive Card").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(PrimaryButtonStyle(color: accent))
                        .disabled(busy != nil)
                    } else {
                        Text("Complete the Champion quest to unlock").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
    }

    private var leaderboardSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            RVSectionTitle(title: "Leaderboard", icon: "list.number", color: accent)
            RVLeaderboardList(rows: leaders, suffix: "pts", accent: accent)
        }
        .padding(16)
        .cardStyle()
    }

    // MARK: Data

    private func load() async {
        do {
            let page = try await Base44Client.shared.list("CommunityEvent", CommunityEvent.self,
                                                          query: ["type": "special_release"], limit: 1)
            guard let e = page.items.first else {
                event = nil
                noEvent = true
                loading = false
                return
            }
            noEvent = false
            let d = try await Engines.community("release_state", ["event_id": e.id])
            let state = try? Base44Client.shared.decode(SpecialEventState.self, d)
            var rewardCard: TradingCard?
            var owns = false
            if let rid = nonEmpty(e.reward_card_id) {
                let cardPage = try? await Base44Client.shared.list("TradingCard", TradingCard.self, query: ["card_id": rid], limit: 1)
                rewardCard = cardPage?.items.first
                let mine = (try? await Engines.myCards(limit: 5, query: ["card_id": rid])) ?? []
                owns = !mine.isEmpty
            }
            let rows = (try? await Engines.leaderboard(e.id)) ?? []
            event = e
            st = state
            card = rewardCard
            ownsCard = owns
            leaders = Array(rows.prefix(8))
            errorText = nil
        } catch {
            if !(error is CancellationError) { errorText = error.localizedDescription }
        }
        loading = false
    }

    /// Runs a quest action (only while the event is live), then reloads the state.
    private func perform(_ key: String, _ op: @escaping @MainActor () async throws -> String) {
        guard isLive else {
            app.show("Quests open on release day", "Boruto Vol. 7 drops on the planned release date.")
            return
        }
        guard busy == nil else { return }
        busy = key
        Task {
            do {
                let toast = try await op()
                app.show(toast)
                app.dataChanged()
                await load()
            } catch {
                app.fail("Action failed", error)
            }
            busy = nil
        }
    }

    private func markVisit() {
        guard let eid = event?.id else { return }
        perform("q1_visit") {
            let d = try await Engines.community("release_visit", ["event_id": eid])
            let o = Engines.json(d)
            if let err = nonEmpty(o["error"] as? String) { throw APIError(status: 0, message: err) }
            return "Visit registered!"
        }
    }

    private func addVolume() {
        perform("q2_collect") {
            let payload: [String: Any] = [
                "manga_id": Self.mangaId,
                "title": Self.mangaTitle,
                "volume_number": 7,
                "total_volumes": 9,
                "cover_image_url": Self.cover,
                "edition": "standard",
                "publisher": "Other",
                "is_shonen_jump": false,
                "status": "plan_to_read"
            ]
            _ = try await Base44Client.shared.create("CollectionItem", payload, RVIgnored.self)
            return "Volume 7 added to your collection!"
        }
    }

    private func logProgress() {
        perform("q3_progress") {
            let payload: [String: Any] = [
                "type": "progress",
                "manga_id": Self.mangaId,
                "title": Self.mangaTitle,
                "volume_number": 7,
                "meta": "Volume 7"
            ]
            _ = try await Base44Client.shared.create("ReadingActivity", payload, RVIgnored.self)
            return "Reading progress logged for Volume 7!"
        }
    }

    private func publishReview() {
        let text = reviewText.trimmingCharacters(in: .whitespacesAndNewlines)
        if isLive && text.isEmpty {
            app.show("Write a short review first", error: true)
            return
        }
        perform("q4_review") {
            let payload: [String: Any] = [
                "manga_id": Self.mangaId,
                "title": Self.mangaTitle,
                "entry_type": "note",
                "content": text
            ]
            _ = try await Base44Client.shared.create("JournalEntry", payload, RVIgnored.self)
            reviewText = ""
            reviewOpen = false
            return "Review published!"
        }
    }

    private func claim() async {
        guard let e = event, busy == nil else { return }
        busy = "claim"
        defer { busy = nil }
        do {
            let d = try await Engines.community("claim", ["event_id": e.id])
            let o = Engines.json(d)
            if (o["ok"] as? Bool) == true {
                app.show("Exclusive card claimed!", "Boruto Volume 7 — Release Edition was added to your collection.")
                app.celebrate()
                app.dataChanged()
                await load()
            } else {
                app.show("Could not claim card", rvError(o, "Please try again later."), error: true)
            }
        } catch {
            app.fail("Could not claim card", error)
        }
    }
}
