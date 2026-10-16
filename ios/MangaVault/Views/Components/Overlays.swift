import SwiftUI

struct ToastView: View {
    let toast: Toast
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: toast.isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundStyle(toast.isError ? Theme.red : Theme.primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(toast.title).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
                if let m = toast.message, !m.isEmpty {
                    Text(m).font(.caption).foregroundStyle(Theme.muted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(toast.isError ? Theme.red.opacity(0.5) : Theme.border))
        .padding(.horizontal, 16)
        .shadow(color: .black.opacity(0.4), radius: 12, y: 6)
    }
}

/// Lightweight confetti burst (replaces canvas-confetti from the web app).
struct ConfettiView: View {
    let trigger: Int
    @State private var particles: [Particle] = []
    @State private var start = Date()

    struct Particle: Identifiable {
        let id = UUID()
        let color: Color
        let x: CGFloat
        let vx: CGFloat
        let vy: CGFloat
        let spin: Double
        let size: CGFloat
        let circle: Bool
    }

    private static let colors = ["#26ccff", "#a25afd", "#ff5e7e", "#88ff5a", "#fcff42", "#ffa62d", "#ff36ff", "#FF7A00"].map(Color.init(hex:))

    var body: some View {
        TimelineView(.animation(paused: particles.isEmpty)) { timeline in
            Canvas { ctx, size in
                let t = CGFloat(timeline.date.timeIntervalSince(start))
                for p in particles {
                    let px = size.width * p.x + p.vx * t
                    let py = size.height * 0.4 + p.vy * t + 450 * t * t
                    let alpha = Double(max(0, 1 - t / 2.6))
                    var c = ctx
                    c.opacity = alpha
                    c.translateBy(x: px, y: py)
                    c.rotate(by: .degrees(p.spin * Double(t) * 360))
                    let rect = CGRect(x: -p.size / 2, y: -p.size / 2, width: p.size, height: p.size * (p.circle ? 1 : 0.6))
                    c.fill(p.circle ? Path(ellipseIn: rect) : Path(rect), with: .color(p.color))
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
        .onChange(of: trigger) { _, _ in burst() }
    }

    private func burst() {
        start = Date()
        particles = (0..<90).map { _ in
            let angle = Double.random(in: (-90 - 35)...(-90 + 35)) * .pi / 180
            let speed = CGFloat.random(in: 350...850)
            return Particle(color: Self.colors.randomElement()!,
                            x: 0.5,
                            vx: CGFloat(cos(angle)) * speed,
                            vy: CGFloat(sin(angle)) * speed,
                            spin: Double.random(in: -2...2),
                            size: CGFloat.random(in: 6...11),
                            circle: Bool.random())
        }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.8))
            particles = []
        }
    }
}
