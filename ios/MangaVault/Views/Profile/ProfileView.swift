import SwiftUI
import PhotosUI
import UIKit

struct ProfileView: View {
    /// nil = the signed-in user's own profile; otherwise a public profile id.
    let profileId: String?
    @Environment(AppState.self) private var app

    @State private var profile: UserProfile?
    @State private var loading = true
    @State private var saving = false
    @State private var level = XP.level(for: 0)
    @State private var volumes = 0
    @State private var genres = 0

    // Edit form
    @State private var displayName = ""
    @State private var bio = ""
    @State private var avatarURL = ""
    @State private var bannerURL = ""
    @State private var profilePublic = false
    @State private var collectionPublic = false
    @State private var avatarPick: PhotosPickerItem?
    @State private var bannerPick: PhotosPickerItem?
    @State private var uploading: String?
    @State private var favManga = ""
    @State private var favCharacters = ""
    @State private var favGenres = ""

    // Someone else's profile (socialProfile)
    @State private var social: SocialProfileResponse?
    @State private var sendingRequest = false
    @State private var requestSent = false

    private var isOwn: Bool { profileId == nil }

    var body: some View {
        ScrollView {
            if loading {
                LoadingView()
            } else if let profile {
                VStack(alignment: .leading, spacing: 20) {
                    headerCard(profile)
                    if isOwn { editCard(profile) }
                    else if social?.is_private == true {
                        Label("This profile is private. Send a friend request to see more.", systemImage: "lock")
                            .font(.subheadline).foregroundStyle(Theme.muted)
                            .frame(maxWidth: .infinity).padding(28).cardStyle()
                    }
                }
                .padding(16)
            } else {
                EmptyState(icon: "person.crop.circle.badge.questionmark", text: "Profile not found or private.")
            }
        }
        .background(Theme.background)
        .navigationTitle(isOwn ? "Profile" : "Reader")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onChange(of: avatarPick) { _, item in Task { await upload(item, field: "avatar") } }
        .onChange(of: bannerPick) { _, item in Task { await upload(item, field: "banner") } }
    }

    private func headerCard(_ p: UserProfile) -> some View {
        let banner = isOwn ? bannerURL : (p.banner_url ?? "")
        let avatar = isOwn ? avatarURL : (p.avatar_url ?? "")
        let name = isOwn ? (nonEmpty(displayName) ?? app.displayName) : (nonEmpty(p.display_name) ?? "Reader")
        let featured = (p.featured_achievements ?? []).compactMap { id in AchievementDef.all.first { $0.id == id } }

        return VStack(alignment: .leading, spacing: 0) {
            ZStack {
                if banner.isEmpty {
                    LinearGradient(colors: [Theme.primary.opacity(0.35), Color(hex: "#101010")], startPoint: .topLeading, endPoint: .bottomTrailing)
                } else {
                    RemoteImage(url: banner)
                }
            }
            .frame(height: 150).frame(maxWidth: .infinity).clipped()

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .bottom) {
                    ZStack {
                        Theme.card
                        if avatar.isEmpty { Image(systemName: "person.fill").font(.largeTitle).foregroundStyle(Theme.muted) }
                        else { RemoteImage(url: avatar) }
                    }
                    .frame(width: 92, height: 92)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.background, lineWidth: 4))
                    Spacer()
                    if (isOwn ? profilePublic : (p.profile_public ?? false)) {
                        Badge(text: "PUBLIC", icon: "globe", fg: Theme.emerald, bg: Theme.emerald.opacity(0.2), ring: Theme.emerald.opacity(0.4))
                    }
                }
                .padding(.top, -46)
                Text(name).font(.title2.weight(.black))
                let bioText = isOwn ? bio : (p.bio ?? "")
                if !bioText.isEmpty { Text(bioText).font(.subheadline).foregroundStyle(Theme.muted) }
                if isOwn {
                    HStack(spacing: 22) {
                        stat("Level", "\(level.level)", Theme.primary)
                        stat("XP", "\(level.total)")
                        stat("Volumes", "\(volumes)")
                        stat("Genres", "\(genres)")
                    }
                    .padding(.top, 4)
                }
                if !isOwn, let social { socialSection(p, social) }
                if !featured.isEmpty {
                    Text("FEATURED ACHIEVEMENTS").font(.caption.weight(.bold)).foregroundStyle(Theme.muted).padding(.top, 4)
                    FlowLayout(spacing: 8) {
                        ForEach(featured) { a in
                            Text("\(a.icon) \(a.name)").font(.caption.weight(.bold))
                                .padding(.horizontal, 10).padding(.vertical, 5)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                        }
                    }
                }
                if !isOwn {
                    tagList("Favorite Manga", p.favorite_manga)
                    tagList("Favorite Characters", p.favorite_characters)
                    tagList("Favorite Genres", p.favorite_genres)
                }
            }
            .padding(18)
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    @ViewBuilder private func tagList(_ title: String, _ values: [String]?) -> some View {
        if let values, !values.isEmpty {
            Text(title.uppercased()).font(.caption.weight(.bold)).foregroundStyle(Theme.muted).padding(.top, 4)
            FlowLayout(spacing: 8) {
                ForEach(values.indices, id: \.self) { idx in
                    Text(values[idx]).font(.caption.weight(.bold))
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                }
            }
        }
    }

    private func socialSection(_ p: UserProfile, _ s: SocialProfileResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 22) {
                stat("Friends", "\(s.friendCount ?? 0)")
                stat("Series", "\(s.seriesCount ?? 0)")
                stat("Volumes", "\(s.volCount ?? 0)")
            }
            HStack(spacing: 10) {
                NavigationLink(value: Route.userCollection(p.id)) {
                    Label("View Collection", systemImage: "books.vertical").frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                if s.friend == true, let uid = p.created_by_id {
                    NavigationLink(value: Route.chat(userId: uid)) {
                        Label("Message", systemImage: "bubble.left.and.bubble.right").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle())
                } else if s.isOwn != true {
                    Button { Task { await sendFriendRequest(p) } } label: {
                        Group {
                            if sendingRequest { ProgressView() }
                            else { Label(requestSent ? "Request Sent" : "Add Friend", systemImage: requestSent ? "clock" : "person.badge.plus") }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(OutlineButtonStyle())
                    .disabled(sendingRequest || requestSent)
                }
            }
            if s.friend == true {
                Label("Friends", systemImage: "checkmark.circle.fill").font(.caption.weight(.bold)).foregroundStyle(Theme.emerald)
            }
        }
        .padding(.top, 6)
    }

    private func sendFriendRequest(_ p: UserProfile) async {
        guard let uid = p.created_by_id else { return }
        sendingRequest = true; defer { sendingRequest = false }
        do {
            try await Social.friends("send", ["target_user_id": uid])
            requestSent = true
            app.show("Friend request sent")
        } catch { app.fail("Failed", error) }
    }

    private func stat(_ label: String, _ value: String, _ color: Color = Theme.foreground) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(Theme.muted)
            Text(value).font(.title3.weight(.black)).foregroundStyle(color)
        }
    }

    private func editCard(_ p: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Edit Profile").font(.headline.weight(.black))
            field("Display Name") {
                TextField("Display name", text: $displayName).textFieldStyle(VaultFieldStyle())
            }
            field("Bio") {
                TextField("Tell others about your taste in manga", text: $bio, axis: .vertical)
                    .lineLimit(2...5).textFieldStyle(VaultFieldStyle())
            }
            HStack(alignment: .top, spacing: 16) {
                field("Avatar") {
                    PhotosPicker(selection: $avatarPick, matching: .images) {
                        uploadLabel(uploading == "avatar")
                    }
                }
                field("Banner") {
                    PhotosPicker(selection: $bannerPick, matching: .images) {
                        uploadLabel(uploading == "banner")
                    }
                }
            }
            field("Favorite Manga (comma separated)") {
                TextField("One Piece, Naruto...", text: $favManga).textFieldStyle(VaultFieldStyle())
            }
            field("Favorite Characters") {
                TextField("Luffy, Zoro...", text: $favCharacters).textFieldStyle(VaultFieldStyle())
            }
            field("Favorite Genres") {
                TextField("Action, Fantasy...", text: $favGenres).textFieldStyle(VaultFieldStyle())
            }
            Toggle(isOn: $profilePublic) { Label("Public profile", systemImage: profilePublic ? "globe" : "lock") }
            Toggle(isOn: $collectionPublic) { Label("Public collection", systemImage: "books.vertical") }

            HStack(spacing: 10) {
                Button { Task { await save(p) } } label: {
                    Group { if saving { ProgressView().tint(.white) } else { Label("Save Profile", systemImage: "square.and.arrow.down") } }
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(saving)
                ShareLink(item: Config.publicProfileURL(p.id)) {
                    Label("Share Link", systemImage: "link").frame(maxWidth: .infinity)
                }
                .buttonStyle(OutlineButtonStyle())
            }
            Text("Your profile link: \(Config.publicProfileURL(p.id).absoluteString)")
                .font(.caption).foregroundStyle(Theme.muted).textSelection(.enabled)
            Text("Uploaded images are stored publicly — anyone with the link can open them.")
                .font(.caption2).foregroundStyle(Theme.muted.opacity(0.8))
        }
        .padding(18)
        .cardStyle()
        .tint(Theme.primary)
    }

    private func field<C: View>(_ label: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.uppercased()).font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func uploadLabel(_ busy: Bool) -> some View {
        HStack(spacing: 6) {
            if busy { ProgressView().controlSize(.small) } else { Image(systemName: "photo.badge.plus") }
            Text("Upload")
        }
        .font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background(Theme.secondary, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Data

    private func load() async {
        do {
            if let profileId {
                let r = try await Social.profile(profileId)
                social = r
                if let u = r.profile, r.error == nil {
                    profile = UserProfile(id: u.id, display_name: u.display_name, bio: u.bio, avatar_url: u.avatar_url,
                                          banner_url: u.banner_url, profile_public: r.profile_public,
                                          collection_public: r.collection_public,
                                          favorite_manga: u.favorite_manga, favorite_characters: u.favorite_characters,
                                          favorite_genres: u.favorite_genres, created_by_id: u.user_id)
                } else {
                    profile = nil
                }
            } else if let uid = app.user?.id {
                let p = try await ProfileStore.shared.ensure(userId: uid)
                profile = p
                displayName = nonEmpty(p.display_name) ?? app.displayName
                bio = p.bio ?? ""
                avatarURL = p.avatar_url ?? ""
                bannerURL = p.banner_url ?? ""
                profilePublic = p.profile_public ?? false
                collectionPublic = p.collection_public ?? false
                favManga = (p.favorite_manga ?? []).joined(separator: ", ")
                favCharacters = (p.favorite_characters ?? []).joined(separator: ", ")
                favGenres = (p.favorite_genres ?? []).joined(separator: ", ")
                let items = try await VaultService.collection()
                let gm = try await VaultService.genreMap(for: items)
                let g = VaultService.genreCount(items, gm)
                volumes = items.count
                genres = g
                if let st = try? await Engines.rewardsState() {
                    level = st.level.info
                } else {
                    level = XP.level(for: XP.base(items: items, genres: g) + p.xpBonus)
                }
            }
        } catch {
            if !(error is CancellationError) { app.fail("Could not load profile", error) }
        }
        loading = false
    }

    private func upload(_ item: PhotosPickerItem?, field: String) async {
        guard let item else { return }
        uploading = field; defer { uploading = nil }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), let img = UIImage(data: data) else { return }
            // Downscale large photos before upload.
            let maxSide: CGFloat = field == "avatar" ? 512 : 1600
            let scale = min(1, maxSide / max(img.size.width, img.size.height))
            let size = CGSize(width: img.size.width * scale, height: img.size.height * scale)
            let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
            let resized = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
            guard let jpeg = resized.jpegData(compressionQuality: 0.85) else { return }
            let url = try await Base44Client.shared.uploadPublicFile(jpeg, filename: "\(field)-\(UUID().uuidString.prefix(8)).jpg", mimeType: "image/jpeg")
            if field == "avatar" { avatarURL = url } else { bannerURL = url }
            app.show("Image uploaded", "Tap Save Profile to keep it.")
        } catch { app.fail("Upload failed", error) }
    }

    private func save(_ p: UserProfile) async {
        saving = true; defer { saving = false }
        do {
            let split: (String) -> [String] = { $0.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
            let data: [String: Any] = [
                "display_name": displayName, "bio": bio, "avatar_url": avatarURL, "banner_url": bannerURL,
                "profile_public": profilePublic, "collection_public": collectionPublic,
                "theme_id": p.theme_id ?? "kaiachu-orange",
                "favorite_manga": split(favManga), "favorite_characters": split(favCharacters), "favorite_genres": split(favGenres)
            ]
            try await Base44Client.shared.update("UserProfile", p.id, data)
            var updated = p
            updated.display_name = displayName; updated.bio = bio; updated.avatar_url = avatarURL; updated.banner_url = bannerURL
            updated.profile_public = profilePublic; updated.collection_public = collectionPublic
            updated.favorite_manga = split(favManga); updated.favorite_characters = split(favCharacters); updated.favorite_genres = split(favGenres)
            await ProfileStore.shared.set(updated)
            profile = updated
            app.show("Profile saved")
        } catch { app.fail("Save failed", error) }
    }
}

struct VaultFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(12)
            .background(Theme.background, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
    }
}
