import SwiftUI
import UIKit

struct WrappedView: View {
    @Environment(AppState.self) private var app
    @Environment(\.displayScale) private var displayScale
    @State private var items: [CollectionItem] = []
    @State private var activities: [ReadingActivity] = []
    @State private var genreMap: [String: String] = [:]
    @State private var profile: UserProfile?
    @State private var loading = true
    @State private var byYear = true
    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var month = String(format: "%04d-%02d", Calendar.current.component(.year, from: Date()), Calendar.current.component(.month, from: Date()))
    @State private var shareImage: ShareableImage?

    struct ShareableImage: Identifiable { let id = UUID(); let image: UIImage }

    private var periodLabel: String { byYear ? String(year) : month }

    private func inPeriod(_ created: String?) -> Bool {
        let d = String((created ?? "").prefix(10))
        guard !d.isEmpty else { return false }
        return byYear ? d.hasPrefix(String(year)) : d.prefix(7) == month
    }

    private var periodItems: [CollectionItem] { items.filter { inPeriod($0.created_date) } }
    private var periodActs: [ReadingActivity] { activities.filter { inPeriod($0.created_date) } }

    private var years: [Int] {
        var s: Set<Int> = [Calendar.current.component(.year, from: Date())]
        for d in items.compactMap(\.created_date) + activities.compactMap(\.created_date) { if let y = Int(d.prefix(4)) { s.insert(y) } }
        return s.sorted(by: >)
    }

    private var months: [String] { (1...12).map { String(format: "%d-%02d", year, $0) } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Manga Wrapped", eyebrowIcon: "sparkles", lead: "MANGA", accent: "WRAPPED",
                             subtitle: "A recap of your real reading activity for a chosen period.")
                HStack(spacing: 10) {
                    Picker("Period", selection: $byYear) {
                        Text("Year").tag(true)
                        Text("Month").tag(false)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 160)
                    if byYear {
                        Picker("Year", selection: $year) { ForEach(years, id: \.self) { Text(String($0)).tag($0) } }
                    } else {
                        Picker("Month", selection: $month) { ForEach(months, id: \.self) { Text($0).tag($0) } }
                    }
                    Spacer()
                }
                if loading {
                    LoadingView()
                } else if periodItems.isEmpty && periodActs.isEmpty {
                    Text("No activity recorded for this period. Add manga, update progress, or change statuses to build your Wrapped.")
                        .font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
                        .padding(28).frame(maxWidth: .infinity).cardStyle()
                } else {
                    wrappedCard
                    Button {
                        let renderer = ImageRenderer(content: wrappedCard.frame(width: 390).padding(12).background(Color(hex: "#101010")).environment(\.colorScheme, .dark))
                        renderer.scale = max(displayScale, 2)
                        if let img = renderer.uiImage { shareImage = ShareableImage(image: img) }
                        else { app.show("Export failed", error: true) }
                    } label: {
                        Label("Share Card", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    if periodActs.filter({ $0.type == "progress" }).isEmpty {
                        Text("Chapter-level reading isn't tracked yet, so progress updates are shown instead of chapters read.")
                            .font(.caption).foregroundStyle(Theme.muted)
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Manga Wrapped")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onChange(of: year) { _, y in
            if !month.hasPrefix(String(y)) { month = String(format: "%d-%02d", y, Calendar.current.component(.month, from: Date())) }
        }
        .sheet(item: $shareImage) { s in ActivityView(items: [s.image]).presentationDetents([.medium, .large]) }
    }

    private var wrappedCard: some View {
        let accent = Theme.primary
        let pi = periodItems, pa = periodActs
        let completed = pa.filter { $0.type == "completed" }.count
        let progress = pa.filter { $0.type == "progress" }.count
        let topGenre = VaultService.genreDistribution(pi, genreMap, top: 1).first?.0
        let topRated = pi.filter { ($0.rating ?? 0) > 0 }.max { ($0.rating ?? 0) < ($1.rating ?? 0) }
        let favorite = pi.first { $0.isFavorite }
        var perDay: [String: Int] = [:]
        pa.forEach { perDay[$0.day, default: 0] += 1 }
        let days: [ChartDatum] = perDay.sorted { $0.key < $1.key }.suffix(30).map { ChartDatum(name: $0.key, value: $0.value) }
        let maxV = max(1, days.map { $0.value }.max() ?? 1)
        let name = nonEmpty(profile?.display_name) ?? app.displayName

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("MANGA VAULT · WRAPPED").font(.caption.weight(.black)).tracking(2).foregroundStyle(accent)
                    Text(periodLabel).font(.system(size: 34, weight: .black)).foregroundStyle(Theme.foreground)
                }
                Spacer()
                Image(systemName: "flame.fill").font(.system(size: 36)).foregroundStyle(accent)
            }
            Text("\(name)'s \(byYear ? "year" : "month") in manga").font(.headline).foregroundStyle(Theme.muted)
            // Non-lazy Grid so ImageRenderer captures it when sharing.
            Grid(horizontalSpacing: 10, verticalSpacing: 10) {
                GridRow {
                    tile("Added", "\(pi.count)", highlight: true)
                    tile("Completed", "\(completed)")
                }
                GridRow {
                    tile("Progress Updates", "\(progress)")
                    tile("Top Genre", topGenre?.capitalized ?? "—", small: true)
                }
            }
            HStack(spacing: 10) {
                infoTile(icon: "star.fill", label: "Highest Rated", value: topRated?.titleText ?? "No ratings yet",
                         extra: topRated.map { "\($0.ratingValue)/10" })
                infoTile(icon: "book.fill", label: "Favorite", value: favorite?.titleText ?? "None marked", extra: nil)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text("READING ACTIVITY").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                if days.isEmpty {
                    Text("No reading activity recorded in this period.").font(.subheadline).foregroundStyle(Theme.muted)
                } else {
                    HStack(alignment: .bottom, spacing: 3) {
                        ForEach(days) { d in
                            RoundedRectangle(cornerRadius: 2).fill(accent)
                                .frame(height: max(4, 90 * CGFloat(d.value) / CGFloat(maxV)))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 90, alignment: .bottom)
                }
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
            Text("mangas.base44.app · Generated from your real collection data.").font(.caption2).foregroundStyle(Theme.muted)
        }
        .padding(20)
        .background(LinearGradient(colors: [Color(hex: "#191919"), Color(hex: "#101010")], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    private func tile(_ label: String, _ value: String, highlight: Bool = false, small: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
            Text(value).font(small ? Font.title3.weight(.black) : Font.system(size: 30, weight: .black))
                .foregroundStyle(highlight ? Theme.primary : Theme.foreground).lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(highlight ? Theme.primary.opacity(0.1) : Theme.card, in: RoundedRectangle(cornerRadius: 12))
    }

    private func infoTile(icon: String, label: String, value: String, extra: String?) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.title3).foregroundStyle(Theme.primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(label.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
                Text(value).font(.subheadline.weight(.bold)).lineLimit(1)
                if let extra { Text(extra).font(.caption).foregroundStyle(Theme.primary) }
            }
            Spacer(minLength: 0)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
    }

    private func load() async {
        do {
            async let a = VaultService.collection()
            async let b = VaultService.activities()
            let its = try await a, acts = try await b
            let gm = try await VaultService.genreMap(for: its)
            items = its; activities = acts; genreMap = gm
            if let uid = app.user?.id { profile = await ProfileStore.shared.current(userId: uid) }
        } catch {
            if !(error is CancellationError) { app.fail("Could not load Wrapped", error) }
        }
        loading = false
    }
}

struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
