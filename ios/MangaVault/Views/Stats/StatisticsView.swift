import SwiftUI
import Charts

struct StatisticsView: View {
    @Environment(AppState.self) private var app
    @State private var items: [CollectionItem] = []
    @State private var activities: [ReadingActivity] = []
    @State private var genreMap: [String: String] = [:]
    @State private var loading = true
    @State private var range = 30

    private static let ranges: [(Int, String)] = [(7, "7 days"), (30, "30 days"), (90, "90 days"), (0, "All time")]

    /// Activities per day (yyyy-MM-dd → count), with empty days filled in for fixed ranges.
    private var activitySeries: [ChartDatum] {
        var counts: [String: Int] = [:]
        if range > 0 {
            for n in 0..<range {
                let d = Calendar.current.date(byAdding: .day, value: -n, to: Date())!
                counts[String(ISO8601DateFormatter().string(from: d).prefix(10))] = 0
            }
        }
        let start: Date? = range == 0 ? nil : Calendar.current.date(byAdding: .day, value: -(range - 1), to: Calendar.current.startOfDay(for: Date()))
        let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"; fmt.timeZone = TimeZone(identifier: "UTC")
        for a in activities {
            let day = a.day
            guard !day.isEmpty else { continue }
            if let start, let d = fmt.date(from: day), d < start { continue }
            counts[day, default: 0] += 1
        }
        return counts.sorted { $0.key < $1.key }.map { ChartDatum(name: range == 0 ? $0.key : String($0.key.dropFirst(5)), value: $0.value) }
    }

    private var rangeLabel: String {
        Self.ranges.first(where: { $0.0 == range })?.1 ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(eyebrow: "Statistics", eyebrowIcon: "chart.bar.xaxis", lead: "YOUR", accent: "STATISTICS",
                             subtitle: "Deep dive into your reading activity — all from your real data.")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar").foregroundStyle(Theme.muted)
                        ForEach(Self.ranges.indices, id: \.self) { idx in
                            let r = Self.ranges[idx]
                            Chip(title: r.1, selected: range == r.0) { range = r.0 }
                        }
                    }
                }
                if loading {
                    LoadingView()
                } else {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        StatCard(icon: "books.vertical", label: "Total Volumes", value: "\(items.count)")
                        StatCard(icon: "waveform.path.ecg", label: "Activities", value: "\(activities.count)")
                        StatCard(icon: "tag", label: "Genres Explored", value: "\(VaultService.genreCount(items, genreMap))")
                        StatCard(icon: "flame.fill", label: "Streak", value: "\(VaultService.streak(activities))d")
                    }
                    SectionCard(title: "Reading Activity (\(rangeLabel))") {
                        let series = activitySeries
                        if series.isEmpty {
                            Text("No activity in this range yet.").font(.subheadline).foregroundStyle(Theme.muted).frame(maxWidth: .infinity, minHeight: 160)
                        } else {
                            Chart(series) { d in
                                LineMark(x: .value("Day", d.name), y: .value("Activities", d.value))
                                    .foregroundStyle(Theme.primary).interpolationMethod(.monotone)
                                    .lineStyle(StrokeStyle(lineWidth: 2))
                            }
                            .chartXAxis { AxisMarks(values: .automatic(desiredCount: 5)) { _ in AxisValueLabel().foregroundStyle(Theme.muted) } }
                            .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.border); AxisValueLabel().foregroundStyle(Theme.muted) } }
                            .frame(height: 220)
                        }
                    }
                    SectionCard(title: "Collection Growth") {
                        let g = ChartData.growth(items)
                        if g.isEmpty { Text("No data yet.").foregroundStyle(Theme.muted) } else { GrowthChart(data: g) }
                    }
                    SectionCard(title: "Status Distribution") {
                        Chart(ChartData.status(items)) { d in
                            BarMark(x: .value("Status", d.name), y: .value("Count", d.value))
                                .foregroundStyle(ChartData.color(forStatusKey: d.key)).cornerRadius(4)
                        }
                        .chartXAxis { AxisMarks { _ in AxisValueLabel().foregroundStyle(Theme.muted) } }
                        .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Theme.border); AxisValueLabel().foregroundStyle(Theme.muted) } }
                        .frame(height: 200)
                    }
                    SectionCard(title: "Genre Distribution") {
                        let g = VaultService.genreDistribution(items, genreMap, top: 10).map { ChartDatum(name: $0.0, value: $0.1) }
                        if g.isEmpty { Text("No data yet.").foregroundStyle(Theme.muted) } else { GenreBarChart(data: g) }
                    }
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Statistics")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        do {
            async let a = VaultService.collection()
            async let b = VaultService.activities()
            let its = try await a, acts = try await b
            let gm = try await VaultService.genreMap(for: its)
            items = its; activities = acts; genreMap = gm
        } catch {
            if !(error is CancellationError) { app.fail("Could not load statistics", error) }
        }
        loading = false
    }
}
