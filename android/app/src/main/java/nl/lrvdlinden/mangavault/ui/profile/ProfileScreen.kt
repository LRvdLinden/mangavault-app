package nl.lrvdlinden.mangavault.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.app.Route
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.core.Config
import nl.lrvdlinden.mangavault.core.Engines
import nl.lrvdlinden.mangavault.core.ProfileStore
import nl.lrvdlinden.mangavault.core.Social
import nl.lrvdlinden.mangavault.core.VaultService
import nl.lrvdlinden.mangavault.core.shareText
import nl.lrvdlinden.mangavault.model.AchievementDef
import nl.lrvdlinden.mangavault.model.CollectionItem
import nl.lrvdlinden.mangavault.model.LevelInfo
import nl.lrvdlinden.mangavault.model.ReadStatus
import nl.lrvdlinden.mangavault.model.SocialProfileResponse
import nl.lrvdlinden.mangavault.model.UserProfile
import nl.lrvdlinden.mangavault.model.XP
import nl.lrvdlinden.mangavault.model.nonEmpty
import nl.lrvdlinden.mangavault.ui.components.Badge
import nl.lrvdlinden.mangavault.ui.components.EmptyState
import nl.lrvdlinden.mangavault.ui.components.LoadingView
import nl.lrvdlinden.mangavault.ui.components.OutlineButton
import nl.lrvdlinden.mangavault.ui.components.PrimaryButton
import nl.lrvdlinden.mangavault.ui.components.RemoteImage
import nl.lrvdlinden.mangavault.ui.components.cardStyle
import nl.lrvdlinden.mangavault.ui.theme.Vault
import nl.lrvdlinden.mangavault.ui.theme.hexColor
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Local XP fallback (same formula as the iOS app's XP.base). */
private fun profileXpBase(items: List<CollectionItem>, genres: Int): Int {
    val n = items.size
    val completed = items.count { it.effectiveStatus == ReadStatus.COMPLETED }
    val rated = items.count { (it.rating ?: 0.0) > 0 }
    val favs = items.count { it.isFavorite }
    val notes = items.count { it.hasNotes }
    return n * 10 + completed * 30 + rated * 15 + favs * 5 + notes * 8 + maxOf(0, genres) * 20
}

private fun profileSplit(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Reads, downscales and JPEG-encodes a picked image (off the main thread). */
private suspend fun profileReadJpeg(context: Context, uri: Uri, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
    val img = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
    val scale = min(1f, maxSide.toFloat() / max(img.width, img.height).toFloat())
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(img, max(1, (img.width * scale).roundToInt()), max(1, (img.height * scale).roundToInt()), true)
    } else img
    val out = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
    out.toByteArray()
}

private class ProfileModel {
    var profile by mutableStateOf<UserProfile?>(null)
    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
    var level by mutableStateOf(XP.level(0))
    var volumes by mutableStateOf(0)
    var genres by mutableStateOf(0)

    // Edit form
    var displayName by mutableStateOf("")
    var bio by mutableStateOf("")
    var avatarURL by mutableStateOf("")
    var bannerURL by mutableStateOf("")
    var profilePublic by mutableStateOf(false)
    var collectionPublic by mutableStateOf(false)
    var uploading by mutableStateOf<String?>(null)
    var favManga by mutableStateOf("")
    var favCharacters by mutableStateOf("")
    var favGenres by mutableStateOf("")

    // Someone else's profile (socialProfile)
    var social by mutableStateOf<SocialProfileResponse?>(null)
    var sendingRequest by mutableStateOf(false)
    var requestSent by mutableStateOf(false)

    suspend fun load(app: AppState, profileId: String?) {
        try {
            if (profileId != null) {
                val r = Social.profile(profileId)
                social = r
                val u = r.profile
                profile = if (u != null && r.error == null) {
                    UserProfile(
                        id = u.id, display_name = u.display_name, bio = u.bio, avatar_url = u.avatar_url,
                        banner_url = u.banner_url, profile_public = r.profile_public, collection_public = r.collection_public,
                        favorite_manga = u.favorite_manga, favorite_characters = u.favorite_characters,
                        favorite_genres = u.favorite_genres, created_by_id = u.user_id,
                    )
                } else null
            } else {
                val uid = app.user?.id
                if (uid != null) {
                    val p = ProfileStore.ensure(uid)
                    profile = p
                    displayName = nonEmpty(p.display_name) ?: app.displayName
                    bio = p.bio ?: ""
                    avatarURL = p.avatar_url ?: ""
                    bannerURL = p.banner_url ?: ""
                    profilePublic = p.profile_public ?: false
                    collectionPublic = p.collection_public ?: false
                    favManga = (p.favorite_manga ?: emptyList()).joinToString(", ")
                    favCharacters = (p.favorite_characters ?: emptyList()).joinToString(", ")
                    favGenres = (p.favorite_genres ?: emptyList()).joinToString(", ")
                    val items = VaultService.collection()
                    val gm = VaultService.genreMap(items)
                    val g = VaultService.genreCount(items, gm)
                    volumes = items.size
                    genres = g
                    val st = runCatching { Engines.rewardsState() }.getOrNull()
                    level = st?.level?.info ?: XP.level(profileXpBase(items, g) + (p.xp_bonus ?: 0.0).toInt())
                }
            }
        } catch (e: Exception) {
            app.fail("Could not load profile", e)
        }
        loading = false
    }

    suspend fun upload(app: AppState, context: Context, uri: Uri, field: String) {
        uploading = field
        try {
            val jpeg = profileReadJpeg(context, uri, if (field == "avatar") 512 else 1600) ?: return
            val name = "$field-${UUID.randomUUID().toString().take(8)}.jpg"
            val url = Base44.uploadPublicFile(jpeg, name, "image/jpeg")
            if (field == "avatar") avatarURL = url else bannerURL = url
            app.show("Image uploaded", "Tap Save Profile to keep it.")
        } catch (e: Exception) {
            app.fail("Upload failed", e)
        } finally {
            uploading = null
        }
    }

    suspend fun save(app: AppState, p: UserProfile) {
        saving = true
        try {
            val data = mapOf<String, Any?>(
                "display_name" to displayName, "bio" to bio, "avatar_url" to avatarURL, "banner_url" to bannerURL,
                "profile_public" to profilePublic, "collection_public" to collectionPublic,
                "theme_id" to (p.theme_id ?: "kaiachu-orange"),
                "favorite_manga" to profileSplit(favManga), "favorite_characters" to profileSplit(favCharacters),
                "favorite_genres" to profileSplit(favGenres),
            )
            Base44.update("UserProfile", p.id, data)
            val updated = p.copy(
                display_name = displayName, bio = bio, avatar_url = avatarURL, banner_url = bannerURL,
                profile_public = profilePublic, collection_public = collectionPublic,
                favorite_manga = profileSplit(favManga), favorite_characters = profileSplit(favCharacters),
                favorite_genres = profileSplit(favGenres),
            )
            ProfileStore.set(updated)
            profile = updated
            app.show("Profile saved")
        } catch (e: Exception) {
            app.fail("Save failed", e)
        } finally {
            saving = false
        }
    }

    suspend fun sendFriendRequest(app: AppState, p: UserProfile) {
        val uid = p.created_by_id ?: return
        sendingRequest = true
        try {
            Social.friends("send", mapOf("target_user_id" to uid))
            requestSent = true
            app.show("Friend request sent")
        } catch (e: Exception) {
            app.fail("Failed", e)
        } finally {
            sendingRequest = false
        }
    }
}

@Composable
fun ProfileScreen(profileId: String?) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(profileId) { ProfileModel() }
    val isOwn = profileId == null

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { model.upload(app, context, uri, "avatar") }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { model.upload(app, context, uri, "banner") }
    }

    LaunchedEffect(profileId) { model.load(app, profileId) }

    Column(
        Modifier.fillMaxSize().background(Vault.background).verticalScroll(rememberScrollState()).imePadding(),
    ) {
        val p = model.profile
        if (model.loading) {
            LoadingView()
        } else if (p != null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ProfileHeaderCard(p, model, isOwn,
                    onCollection = { app.open(Route.UserCollection(p.id)) },
                    onMessage = { uid -> app.open(Route.Chat(uid)) },
                    onAddFriend = { scope.launch { model.sendFriendRequest(app, p) } },
                )
                if (isOwn) {
                    ProfileEditCard(
                        p, model,
                        onPickAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPickBanner = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onSave = { scope.launch { model.save(app, p) } },
                        onShare = { shareText(context, Config.publicProfileUrl(p.id)) },
                    )
                } else if (model.social?.is_private == true) {
                    Row(
                        Modifier.fillMaxWidth().cardStyle().padding(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = Vault.muted, modifier = Modifier.size(16.dp))
                        Text("This profile is private. Send a friend request to see more.", color = Vault.muted, fontSize = 14.sp)
                    }
                }
            }
        } else {
            EmptyState(Icons.Filled.Person, "Profile not found or private.")
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, color: Color = Vault.foreground) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label.uppercase(), color = Vault.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTags(title: String, values: List<String>?) {
    if (values.isNullOrEmpty()) return
    Text(title.uppercase(), color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in values) ProfileTag(v)
    }
}

@Composable
private fun ProfileTag(text: String) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text, color = Vault.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Vault.card, shape).border(1.dp, Vault.border, shape).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeaderCard(
    p: UserProfile,
    model: ProfileModel,
    isOwn: Boolean,
    onCollection: () -> Unit,
    onMessage: (String) -> Unit,
    onAddFriend: () -> Unit,
) {
    val app = LocalApp.current
    val banner = if (isOwn) model.bannerURL else (p.banner_url ?: "")
    val avatar = if (isOwn) model.avatarURL else (p.avatar_url ?: "")
    val name = if (isOwn) (nonEmpty(model.displayName) ?: app.displayName) else (nonEmpty(p.display_name) ?: "Reader")
    val featured = (p.featured_achievements ?: emptyList()).mapNotNull { id -> AchievementDef.all.firstOrNull { it.id == id } }
    val shape = RoundedCornerShape(18.dp)

    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, Vault.border, shape)) {
        Box(Modifier.fillMaxWidth().height(196.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                if (banner.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Vault.primary.copy(alpha = 0.35f), hexColor("#101010")))))
                } else {
                    RemoteImage(banner, Modifier.fillMaxSize())
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                val avShape = RoundedCornerShape(18.dp)
                Box(
                    Modifier.size(92.dp).clip(avShape).background(Vault.card).border(4.dp, Vault.background, avShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatar.isEmpty()) Icon(Icons.Filled.Person, null, tint = Vault.muted, modifier = Modifier.size(40.dp))
                    else RemoteImage(avatar, Modifier.fillMaxSize())
                }
                Spacer(Modifier.weight(1f))
                if (if (isOwn) model.profilePublic else (p.profile_public ?: false)) {
                    Badge("PUBLIC", Modifier.padding(bottom = 6.dp), icon = Icons.Filled.Public, fg = Vault.emerald,
                        bg = Vault.emerald.copy(alpha = 0.2f), ring = Vault.emerald.copy(alpha = 0.4f))
                }
            }
        }
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, color = Vault.foreground, fontSize = 22.sp, fontWeight = FontWeight.Black)
                val bioText = if (isOwn) model.bio else (p.bio ?: "")
                if (bioText.isNotEmpty()) Text(bioText, color = Vault.muted, fontSize = 14.sp)
                if (isOwn) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        val lv: LevelInfo = model.level
                        ProfileStat("Level", "${lv.level}", Vault.primary)
                        ProfileStat("XP", "${lv.total}")
                        ProfileStat("Volumes", "${model.volumes}")
                        ProfileStat("Genres", "${model.genres}")
                    }
                }
                val s = model.social
                if (!isOwn && s != null) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            ProfileStat("Friends", "${s.friendCount}")
                            ProfileStat("Series", "${s.seriesCount}")
                            ProfileStat("Volumes", "${s.volCount}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("View Collection", onClick = onCollection, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.LibraryBooks)
                            val uid = p.created_by_id
                            if (s.friend == true && uid != null) {
                                OutlineButton("Message", onClick = { onMessage(uid) }, modifier = Modifier.weight(1f), icon = Icons.Filled.Forum)
                            } else if (s.isOwn != true) {
                                if (model.sendingRequest) {
                                    Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = Vault.primary, strokeWidth = 2.dp)
                                    }
                                } else {
                                    OutlineButton(
                                        if (model.requestSent) "Request Sent" else "Add Friend",
                                        onClick = onAddFriend,
                                        modifier = Modifier.weight(1f),
                                        enabled = !model.requestSent,
                                        icon = if (model.requestSent) Icons.Filled.Schedule else Icons.Filled.PersonAdd,
                                    )
                                }
                            }
                        }
                        if (s.friend == true) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Vault.emerald, modifier = Modifier.size(14.dp))
                                Text("Friends", color = Vault.emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (featured.isNotEmpty()) {
                    Text("FEATURED ACHIEVEMENTS", color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in featured) ProfileTag("${a.icon} ${a.name}")
                    }
                }
                if (!isOwn) {
                    ProfileTags("Favorite Manga", p.favorite_manga)
                    ProfileTags("Favorite Characters", p.favorite_characters)
                    ProfileTags("Favorite Genres", p.favorite_genres)
                }
            }
        }
    }
}

@Composable
private fun profileFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vault.foreground, unfocusedTextColor = Vault.foreground,
    focusedBorderColor = Vault.primary, unfocusedBorderColor = Vault.border,
    focusedContainerColor = Vault.background, unfocusedContainerColor = Vault.background,
    cursorColor = Vault.primary,
    focusedPlaceholderColor = Vault.muted, unfocusedPlaceholderColor = Vault.muted,
)

@Composable
private fun ProfileField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = Vault.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ProfileTextField(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 5,
        shape = RoundedCornerShape(10.dp),
        colors = profileFieldColors(),
    )
}

@Composable
private fun ProfileUploadButton(busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.clip(shape).background(Vault.secondary, shape).clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Vault.primary, strokeWidth = 2.dp)
        else Icon(Icons.Filled.Image, null, tint = Vault.foreground, modifier = Modifier.size(16.dp))
        Text("Upload", color = Vault.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileToggle(label: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Vault.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Vault.foreground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Vault.primary, checkedThumbColor = Color.White))
    }
}

@Composable
private fun ProfileEditCard(
    p: UserProfile,
    model: ProfileModel,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().cardStyle().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Edit Profile", color = Vault.foreground, fontSize = 17.sp, fontWeight = FontWeight.Black)
        ProfileField("Display Name") { ProfileTextField(model.displayName, { model.displayName = it }, "Display name") }
        ProfileField("Bio") { ProfileTextField(model.bio, { model.bio = it }, "Tell others about your taste in manga", singleLine = false) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileField("Avatar", Modifier.weight(1f)) { ProfileUploadButton(model.uploading == "avatar", onPickAvatar) }
            ProfileField("Banner", Modifier.weight(1f)) { ProfileUploadButton(model.uploading == "banner", onPickBanner) }
        }
        ProfileField("Favorite Manga (comma separated)") { ProfileTextField(model.favManga, { model.favManga = it }, "One Piece, Naruto...") }
        ProfileField("Favorite Characters") { ProfileTextField(model.favCharacters, { model.favCharacters = it }, "Luffy, Zoro...") }
        ProfileField("Favorite Genres") { ProfileTextField(model.favGenres, { model.favGenres = it }, "Action, Fantasy...") }
        ProfileToggle("Public profile", if (model.profilePublic) Icons.Filled.Public else Icons.Filled.Lock, model.profilePublic) { model.profilePublic = it }
        ProfileToggle("Public collection", Icons.AutoMirrored.Filled.LibraryBooks, model.collectionPublic) { model.collectionPublic = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Save Profile", onClick = onSave, modifier = Modifier.weight(1f), loading = model.saving, icon = Icons.Filled.Check)
            OutlineButton("Share Link", onClick = onShare, modifier = Modifier.weight(1f), icon = Icons.Filled.Share)
        }
        SelectionContainer {
            Text("Your profile link: ${Config.publicProfileUrl(p.id)}", color = Vault.muted, fontSize = 12.sp)
        }
        Text("Uploaded images are stored publicly — anyone with the link can open them.", color = Vault.muted.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}
