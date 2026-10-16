import SwiftUI

/// Colours taken from the web app's CSS variables (theme "kaiachu-orange").
enum Theme {
    static let primary = Color(red: 255/255, green: 122/255, blue: 0/255)       // #FF7A00
    static let primaryHover = Color(red: 255/255, green: 81/255, blue: 0/255)
    static let background = Color(red: 18/255, green: 18/255, blue: 18/255)     // #121212
    static let card = Color(red: 27/255, green: 27/255, blue: 27/255)           // #1B1B1B
    static let secondary = Color(red: 34/255, green: 34/255, blue: 34/255)      // #222222
    static let border = Color(red: 51/255, green: 51/255, blue: 51/255)         // #333333
    static let foreground = Color(red: 245/255, green: 245/255, blue: 245/255)
    static let muted = Color(red: 163/255, green: 163/255, blue: 163/255)
    static let emerald = Color(red: 16/255, green: 185/255, blue: 129/255)
    static let sky = Color(red: 56/255, green: 189/255, blue: 248/255)
    static let orange = Color(red: 251/255, green: 146/255, blue: 60/255)
    static let amber = Color(red: 245/255, green: 158/255, blue: 11/255)
    static let red = Color(red: 239/255, green: 68/255, blue: 68/255)

    static func statusColor(_ s: ReadStatus) -> Color {
        switch s {
        case .reading: return primary
        case .completed: return emerald
        case .plan_to_read: return sky
        case .on_hold: return Color(red: 234/255, green: 88/255, blue: 12/255)
        case .dropped: return Color(white: 0.64)
        }
    }
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

// MARK: - Reusable bits

struct ScreenHeader: View {
    var eyebrow: String? = nil
    var eyebrowIcon: String? = nil
    let lead: String
    let accent: String
    var accentColor: Color = Theme.primary
    var subtitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let eyebrow {
                HStack(spacing: 6) {
                    if let eyebrowIcon { Image(systemName: eyebrowIcon) }
                    Text(eyebrow.uppercased()).tracking(2)
                }
                .font(.caption.weight(.bold))
                .foregroundStyle(Theme.primary)
            }
            (Text(lead + " ").foregroundStyle(Theme.foreground) + Text(accent).foregroundStyle(accentColor))
                .font(.system(size: 32, weight: .black))
                .tracking(-1)
            if let subtitle {
                Text(subtitle).font(.subheadline).foregroundStyle(Theme.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct StatCard: View {
    let icon: String
    let label: String
    let value: String
    var accent: Color = Theme.primary

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon).foregroundStyle(accent)
                Text(label.uppercased()).tracking(1).lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.caption2.weight(.bold))
            .foregroundStyle(Theme.muted)
            Text(value)
                .font(.title2.weight(.black))
                .foregroundStyle(Theme.foreground)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .cardStyle()
    }
}

struct SectionCard<Content: View>: View {
    let title: String
    var trailing: AnyView? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title.uppercased()).font(.footnote.weight(.black)).tracking(1).foregroundStyle(Theme.muted)
                Spacer()
                if let trailing { trailing }
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }
}

struct CardStyle: ViewModifier {
    var highlighted = false
    func body(content: Content) -> some View {
        content
            .background(Theme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(highlighted ? Theme.primary.opacity(0.45) : Theme.border, lineWidth: 1))
    }
}

extension View {
    func cardStyle(highlighted: Bool = false) -> some View { modifier(CardStyle(highlighted: highlighted)) }
}

struct Chip: View {
    let title: String
    var icon: String? = nil
    let selected: Bool
    var selectedColor: Color = Theme.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { Image(systemName: icon) }
                Text(title)
            }
            .font(.caption.weight(.bold))
            .padding(.horizontal, 12).padding(.vertical, 7)
            .foregroundStyle(selected ? Color.white : Theme.muted)
            .background(selected ? selectedColor : Theme.card, in: RoundedRectangle(cornerRadius: 9))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(selected ? Color.clear : Theme.border))
        }
        .buttonStyle(.plain)
    }
}

struct Badge: View {
    let text: String
    var icon: String? = nil
    var fg: Color = .white
    var bg: Color = Theme.primary
    var ring: Color? = nil

    var body: some View {
        HStack(spacing: 3) {
            if let icon { Image(systemName: icon) }
            Text(text)
        }
        .font(.system(size: 9, weight: .black))
        .padding(.horizontal, 6).padding(.vertical, 3)
        .foregroundStyle(fg)
        .background(bg, in: RoundedRectangle(cornerRadius: 4))
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(ring ?? .clear, lineWidth: 1))
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = Theme.primary
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 16).frame(minHeight: 44)
            .background(color.opacity(configuration.isPressed ? 0.8 : 1), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct OutlineButtonStyle: ButtonStyle {
    var color: Color = Theme.foreground
    var border: Color = Theme.border
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 16).frame(minHeight: 44)
            .background(Theme.card.opacity(configuration.isPressed ? 1 : 0.4), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(border))
    }
}

struct StarRating: View {
    /// Rating on the web app's 0–10 scale (each star = 2 points).
    let value: Int
    var size: CGFloat = 13
    var onSet: ((Int) -> Void)? = nil

    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                let full = value >= i * 2
                let half = !full && value >= i * 2 - 1
                Button {
                    onSet?(i * 2)
                } label: {
                    Image(systemName: full ? "star.fill" : (half ? "star.leadinghalf.filled" : "star"))
                        .font(.system(size: size))
                        .foregroundStyle(full || half ? Theme.orange : Color(white: 0.35))
                }
                .buttonStyle(.plain)
                .disabled(onSet == nil)
            }
        }
    }
}

struct EmptyState: View {
    let icon: String
    let text: String
    var buttonTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 44)).foregroundStyle(Theme.muted.opacity(0.5))
            Text(text).font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            if let buttonTitle, let action {
                Button(buttonTitle, action: action).buttonStyle(PrimaryButtonStyle())
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }
}

struct LoadingView: View {
    var body: some View {
        ProgressView().tint(Theme.primary).controlSize(.large)
            .frame(maxWidth: .infinity).padding(.vertical, 80)
    }
}

struct ProgressBar: View {
    let value: Double
    var height: CGFloat = 8
    var color: Color = Theme.primary
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.secondary)
                Capsule().fill(color)
                    .frame(width: geo.size.width * max(0, min(1, value)))
                    .shadow(color: color.opacity(0.5), radius: 6)
            }
        }
        .frame(height: height)
    }
}

let gridColumns = [GridItem(.adaptive(minimum: 150, maximum: 220), spacing: 14)]
let smallGridColumns = [GridItem(.adaptive(minimum: 100, maximum: 150), spacing: 12)]
