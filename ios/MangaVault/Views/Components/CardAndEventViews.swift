import SwiftUI

// MARK: - Trading card

struct TradingCardView: View {
    enum Size { case sm, md, lg
        var width: CGFloat { self == .lg ? 192 : (self == .sm ? 96 : 128) }
    }
    let card: TradingCard
    var owned = false
    var count: Int = 0
    var favorite = false
    var size: Size = .md
    var onTap: (() -> Void)? = nil
    var onFavorite: (() -> Void)? = nil

    var body: some View {
        let r = card.rarityValue
        let color = Color(hex: r.hex)
        VStack(spacing: 6) {
            ZStack {
                if owned, let url = nonEmpty(card.artwork_url) {
                    RemoteImage(url: url)
                } else if owned {
                    LinearGradient(colors: [color, Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .overlay(VStack(spacing: 4) {
                            Image(systemName: "photo").font(.title2)
                            Text("No image").font(.caption2)
                        }.foregroundStyle(.white.opacity(0.7)))
                } else {
                    Theme.secondary.overlay(Image(systemName: "lock.fill").font(.title2).foregroundStyle(Theme.muted))
                }
                LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom)
                VStack {
                    HStack(alignment: .top) {
                        Text(r.label).font(.system(size: 9, weight: .black))
                            .padding(.horizontal, 6).padding(.vertical, 3)
                            .background(color, in: RoundedRectangle(cornerRadius: 4))
                            .foregroundStyle(.white)
                        Spacer()
                        if count > 1 {
                            Text("×\(count)").font(.system(size: 10, weight: .black))
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(.black.opacity(0.7), in: Capsule())
                        }
                    }
                    if card.isLimited {
                        Text("Limited Event").font(.system(size: 8, weight: .black)).foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Theme.primary, in: Capsule())
                    }
                    Spacer()
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(card.title).font(.system(size: size == .sm ? 9 : 11, weight: .black)).foregroundStyle(.white).lineLimit(2)
                            Text((card.category ?? "").capitalized).font(.system(size: 8, weight: .bold)).foregroundStyle(.white.opacity(0.6))
                        }
                        Spacer(minLength: 0)
                        if favorite { Image(systemName: "star.fill").font(.caption2).foregroundStyle(Theme.orange) }
                    }
                }
                .padding(6)
                if card.card_id.hasPrefix("christmas") {
                    RoundedRectangle(cornerRadius: 12).stroke(Theme.red, style: StrokeStyle(lineWidth: 3, dash: [6, 4]))
                }
            }
            .frame(width: size.width, height: size.width * 1.4)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(color, lineWidth: 2))
            .shadow(color: color.opacity(0.5), radius: 10, y: 6)
            .onTapGesture { onTap?() }

            if let onFavorite, owned {
                Button(action: onFavorite) {
                    Text(favorite ? "★ Favorite" : "☆ Favorite").font(.system(size: 10, weight: .bold))
                        .frame(width: size.width).padding(.vertical, 5)
                        .foregroundStyle(favorite ? .white : Theme.muted)
                        .background(favorite ? Theme.primary : Theme.secondary, in: RoundedRectangle(cornerRadius: 6))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Countdown

struct CountdownView: View {
    let target: Date?
    var compact = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            let s = max(0, Int((target ?? ctx.date).timeIntervalSince(ctx.date)))
            if s <= 0 {
                Text("It's here!").font(.headline.weight(.black)).foregroundStyle(Theme.primary)
            } else {
                HStack(spacing: compact ? 4 : 8) {
                    unit(s / 86_400, "Days")
                    unit(s % 86_400 / 3600, "Hrs")
                    unit(s % 3600 / 60, "Min")
                    unit(s % 60, "Sec")
                }
            }
        }
    }

    private func unit(_ v: Int, _ l: String) -> some View {
        VStack(spacing: 1) {
            Text(String(format: "%02d", v)).font(.system(size: compact ? 16 : 22, weight: .black, design: .monospaced))
            Text(l.uppercased()).font(.system(size: 8, weight: .bold)).foregroundStyle(Theme.muted)
        }
        .frame(minWidth: compact ? 34 : 46)
        .padding(.vertical, compact ? 4 : 6)
        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 8))
    }
}

/// Falling snow + string lights (web: SnowLayer).
struct SnowLayer: View {
    var lights = true
    private typealias Flake = (x: Double, size: CGFloat, dur: Double, delay: Double)
    private static let flakes: [Flake] = makeFlakes()

    private static let bulbColors: [Color] = [Theme.primary, Color.white, Theme.red, Theme.emerald]

    private func lightBulb(index i: Int, time t: Double) -> some View {
        let c: Color = Self.bulbColors[i % 4]
        let phase: Double = t * 3.0 + Double(i % 4)
        let alpha: Double = 0.5 + 0.5 * sin(phase)
        return Circle().fill(c).frame(width: 6, height: 6).shadow(color: c, radius: 4)
            .opacity(alpha)
            .frame(maxWidth: .infinity)
    }

    private func flakePosition(_ f: Flake, time t: Double, size: CGSize) -> CGPoint {
        let raw: Double = (t + f.delay) / f.dur
        let progress: CGFloat = CGFloat(raw.truncatingRemainder(dividingBy: 1))
        let x: CGFloat = size.width * CGFloat(f.x)
        let travel: CGFloat = size.height + 20
        let y: CGFloat = travel * progress - 10
        return CGPoint(x: x, y: y)
    }

    private func flake(index i: Int, time t: Double, size: CGSize) -> some View {
        let f: Flake = Self.flakes[i]
        let pos: CGPoint = flakePosition(f, time: t, size: size)
        return Text("❄").font(.system(size: f.size)).foregroundStyle(.white.opacity(0.8))
            .position(x: pos.x, y: pos.y)
    }

    private static func makeFlakes() -> [Flake] {
        var out: [Flake] = []
        for t in 0..<18 {
            let x: Double = Double((t * 37) % 100) / 100.0
            let size: CGFloat = CGFloat(8 + (t * 7) % 10)
            let dur: Double = Double(6 + (t * 3) % 7)
            let delay: Double = (Double(t) * 0.7).truncatingRemainder(dividingBy: 6.0)
            out.append((x: x, size: size, dur: dur, delay: delay))
        }
        return out
    }

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation) { ctx in
                let t = ctx.date.timeIntervalSinceReferenceDate
                ZStack(alignment: .topLeading) {
                    ForEach(0..<Self.flakes.count, id: \.self) { i in
                        flake(index: i, time: t, size: geo.size)
                    }
                    if lights {
                        HStack(spacing: 0) {
                            ForEach(0..<14, id: \.self) { i in
                                lightBulb(index: i, time: t)
                            }
                        }
                        .padding(.top, 4)
                    }
                }
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Event banner (communityEngine.banner) — shown on Home & Catalog

struct EventBannerView: View {
    @State private var data: EventBannerData?

    var body: some View {
        Group {
            if let d = data, let kind = d.kind {
                content(d, kind: kind)
            }
        }
        .task {
            while !Task.isCancelled {
                if let b = try? await Engines.banner() { data = b }
                try? await Task.sleep(for: .seconds(60))
            }
        }
    }

    @ViewBuilder private func content(_ d: EventBannerData, kind: String) -> some View {
        if kind == "neutral" {
            VStack(alignment: .leading, spacing: 4) {
                Text("MANGAVAULT").font(.caption.weight(.black)).tracking(2).foregroundStyle(Theme.primary)
                Text("Your manga journey continues").font(.headline.weight(.black))
                Text("No event scheduled right now. Keep reading and building your vault!").font(.caption).foregroundStyle(Theme.muted)
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading).cardStyle()
        } else if let e = d.event {
            let live = d.status == "live"
            let ended = d.status == "ended"
            let accent = Color(hex: e.accent)
            let route: Route = kind == "christmas" ? .christmasEvent : ((kind == "event" || kind == "grand") ? .event(e.id) : .borutoEvent)
            NavigationLink(value: route) {
                ZStack(alignment: .bottomLeading) {
                    if let img = e.heroImage { RemoteImage(url: img) } else {
                        LinearGradient(colors: [accent, Theme.background], startPoint: .topLeading, endPoint: .bottomTrailing)
                    }
                    LinearGradient(colors: [.black.opacity(0.92), .black.opacity(0.45)], startPoint: .leading, endPoint: .trailing)
                    if e.decoration == "snow" || kind == "christmas" { SnowLayer(lights: true) }
                    else if e.decoration == "lights" { SnowLayer(lights: true) }
                    VStack(alignment: .leading, spacing: 6) {
                        Text(live ? "● Live now" : (ended ? "Event ended" : "Coming soon"))
                            .font(.caption.weight(.black)).foregroundStyle(accent)
                        Text(kind == "christmas" ? "🎄 CHRISTMAS READING FESTIVAL" : e.displayTitle)
                            .font(.title3.weight(.black)).foregroundStyle(.white).multilineTextAlignment(.leading)
                        if let sub = nonEmpty(e.subtitle) ?? nonEmpty(e.short_description) {
                            Text(sub).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(2)
                        }
                        Text("\(e.start_date ?? "") → \(e.end_date ?? "")").font(.caption2.weight(.bold)).foregroundStyle(.white.opacity(0.6))
                        if let p = d.participation, d.max_points > 0 {
                            HStack {
                                Text("Your points").font(.caption2.weight(.bold))
                                Spacer()
                                Text("\(p.progress)/\(d.max_points)").font(.caption2.weight(.black)).foregroundStyle(accent)
                            }
                            .foregroundStyle(.white.opacity(0.8))
                            ProgressBar(value: Double(p.progress) / Double(max(d.max_points, 1)), height: 5, color: accent)
                        }
                        if !ended {
                            Text(live ? "ENDS IN" : "STARTS IN").font(.caption2.weight(.black)).foregroundStyle(.white.opacity(0.7)).padding(.top, 2)
                            CountdownView(target: ServerDate.parse(d.target), compact: true).foregroundStyle(.white)
                        }
                    }
                    .padding(16)
                }
                .frame(maxWidth: .infinity, minHeight: 210)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.5)))
            }
            .buttonStyle(.plain)
        }
    }
}
