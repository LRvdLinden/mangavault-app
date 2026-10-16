import SwiftUI
import UIKit

// MARK: - Image loading with memory + disk cache

final class ImageCache: @unchecked Sendable {
    static let shared = ImageCache()
    private let memory = NSCache<NSURL, UIImage>()
    private let session: URLSession

    private init() {
        memory.countLimit = 400
        let cfg = URLSessionConfiguration.default
        cfg.urlCache = URLCache(memoryCapacity: 30 * 1024 * 1024, diskCapacity: 300 * 1024 * 1024)
        cfg.requestCachePolicy = .returnCacheDataElseLoad
        session = URLSession(configuration: cfg)
    }

    func cached(_ url: URL) -> UIImage? { memory.object(forKey: url as NSURL) }

    func load(_ url: URL) async -> UIImage? {
        if let img = cached(url) { return img }
        guard let data = try? await session.data(from: url).0, let img = UIImage(data: data) else { return nil }
        memory.setObject(img, forKey: url as NSURL)
        return img
    }
}

struct RemoteImage: View {
    let url: String?
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else if !failed, url != nil {
                Rectangle().fill(Theme.secondary).overlay(ProgressView().tint(Theme.muted).controlSize(.small))
            } else {
                Rectangle().fill(Theme.secondary)
            }
        }
        .task(id: url) {
            guard let s = url, let u = URL(string: s) else { image = nil; failed = true; return }
            if let img = ImageCache.shared.cached(u) { image = img; return }
            image = nil; failed = false
            if let img = await ImageCache.shared.load(u) { image = img } else { failed = true }
        }
    }
}

// MARK: - Manga cover (real cover or generated placeholder)

struct CoverInfo {
    var title: String
    var coverURL: String?
    var coverSeed: String?
    var publisher: String?
    var isOmnibus: Bool
    var isShonenJump: Bool
    var volumesText: String

    init(manga m: Manga) {
        title = m.title; coverURL = nonEmpty(m.cover_image_url); coverSeed = m.cover_seed
        publisher = m.publisher; isOmnibus = m.isOmnibus; isShonenJump = m.isShonenJump
        volumesText = m.isOmnibus ? (nonEmpty(m.volumes_included) ?? "OMNIBUS") : "\(max(m.totalVolumes, 1)) VOLUMES"
    }

    init(item i: CollectionItem) {
        title = i.titleText; coverURL = i.displayCover; coverSeed = i.cover_seed
        publisher = i.publisher; isOmnibus = i.isOmnibus; isShonenJump = i.is_shonen_jump ?? false
        volumesText = i.isOmnibus ? (nonEmpty(i.volumes_included) ?? "OMNIBUS") : i.volumeLabel.uppercased()
    }

    init(wish w: WishlistItem) {
        title = w.title ?? ""; coverURL = w.displayCover; coverSeed = w.cover_seed
        publisher = w.publisher; isOmnibus = w.isOmnibus; isShonenJump = w.is_shonen_jump ?? false
        volumesText = w.volumeLabel.uppercased()
    }
}

struct CoverView: View {
    let info: CoverInfo
    var cornerRadius: CGFloat = 10

    var body: some View {
        Color.clear
            .aspectRatio(2/3, contentMode: .fit)
            .overlay {
                if let url = info.coverURL {
                    RemoteImage(url: url)
                } else {
                    GeneratedCover(info: info)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).stroke(Color.white.opacity(0.1)))
    }
}

/// Port of the web app's gradient placeholder cover (same palette and hash).
struct GeneratedCover: View {
    let info: CoverInfo

    private static let palette: [(String, String)] = [
        ("#ff6b00", "#1a0a00"), ("#ff3d00", "#0d0d0d"), ("#ff8f00", "#0a0a0a"), ("#e65100", "#000000"),
        ("#ff6b00", "#0a0500"), ("#bf360c", "#050505"), ("#ff7000", "#100808"), ("#cc4400", "#080808")
    ]

    static func hash(_ s: String) -> Int {
        var t: Int32 = 0
        for u in s.utf16 { t = (t &<< 5) &- t &+ Int32(u) }
        return Int(t.magnitude)
    }

    var body: some View {
        let key = nonEmpty(info.coverSeed) ?? (info.title.isEmpty ? "manga" : info.title)
        let p = Self.palette[Self.hash(key) % Self.palette.count]
        ZStack {
            LinearGradient(colors: [Color(hex: p.0), Color(hex: p.1)], startPoint: .topLeading, endPoint: .bottomTrailing)
            RadialGradient(colors: [Color.white.opacity(0.3), .clear], center: UnitPoint(x: 0.3, y: 0.15), startRadius: 0, endRadius: 120)
            VStack(alignment: .leading) {
                HStack {
                    if info.isOmnibus {
                        Badge(text: "OMNIBUS", fg: Theme.primary, bg: .black, ring: Theme.primary.opacity(0.5))
                    } else if info.isShonenJump {
                        Badge(text: "SJ", fg: .black, bg: .white)
                    }
                    Spacer()
                    Text((info.publisher ?? "").uppercased())
                        .font(.system(size: 8, weight: .bold)).foregroundStyle(.white.opacity(0.6)).lineLimit(1)
                }
                Spacer()
                Rectangle().fill(.white.opacity(0.8)).frame(width: 24, height: 2)
                Text(info.title.uppercased())
                    .font(.system(size: 15, weight: .black))
                    .foregroundStyle(.white)
                    .lineLimit(3)
                    .shadow(color: .black.opacity(0.6), radius: 0, x: 2, y: 2)
                Text(info.volumesText)
                    .font(.system(size: 10, weight: .bold)).foregroundStyle(.white.opacity(0.7))
            }
            .padding(10)
        }
    }
}

// MARK: - Catalog card

struct MangaCard: View {
    let manga: Manga
    var owned = false
    var showGenre = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            CoverView(info: CoverInfo(manga: manga))
                .overlay(alignment: .topTrailing) {
                    Group {
                        if owned {
                            Badge(text: "OWNED", icon: "checkmark", bg: Theme.emerald)
                        } else if manga.isOmnibus {
                            Badge(text: "3-IN-1", icon: "books.vertical", fg: Theme.primary, bg: Theme.background.opacity(0.8), ring: Theme.primary.opacity(0.5))
                        } else if manga.isShonenJump {
                            Badge(text: "SJ")
                        }
                    }
                    .padding(8)
                }
                .shadow(color: .black.opacity(0.4), radius: 8, y: 4)
            Text(manga.title).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground).lineLimit(1)
            HStack {
                Text(subtitle).font(.caption).foregroundStyle(Theme.muted).lineLimit(1)
                Spacer(minLength: 4)
                Text(manga.priceValue.euro).font(.caption.weight(.bold)).foregroundStyle(Theme.primary)
            }
        }
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        if showGenre { return nonEmpty(manga.genre) ?? manga.publisher ?? "" }
        return manga.isOmnibus ? (nonEmpty(manga.volumes_included) ?? "Omnibus") : "\(manga.totalVolumes) vols"
    }
}
