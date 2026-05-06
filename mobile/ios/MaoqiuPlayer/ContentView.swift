import SwiftUI
import AVKit
import PhotosUI
import UniformTypeIdentifiers

// MARK: - Identifiable wrapper for image arrays
struct ImageSet: Identifiable {
    let id = UUID()
    let items: [MediaItem]
}

// MARK: - ContentView (首页)
struct ContentView: View {
    @EnvironmentObject var store: MediaStore
    @State private var path = NavigationPath()
    @State private var showFileImporter = false
    
    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                store.colors.bg.ignoresSafeArea()
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // 大标题
                        VStack(alignment: .leading, spacing: 6) {
                            Text("毛球播放器")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(store.colors.text)
                            Text("MaoqiuPlayer · 你的本地媒体中心")
                                .font(.subheadline)
                                .foregroundColor(store.colors.subtext)
                        }
                        .padding(.bottom, 16)
                        
                        // 分割线
                        Rectangle()
                            .fill(store.colors.divider)
                            .frame(height: 1)
                        
                        // 搜索栏
                        SearchBar(text: .constant(""), placeholder: "搜索本地媒体") {
                            path.append(NavDestination.library(.all, ""))
                        }
                        .padding(.top, 18)
                        
                        // 功能入口 - 2列网格
                        Text("主要功能")
                            .font(.subheadline.bold())
                            .foregroundColor(Color(hex: 0x9fa7b2))
                            .padding(.top, 18)
                            .padding(.bottom, 8)
                        
                        LazyVGrid(columns: [
                            GridItem(.flexible(), spacing: 8),
                            GridItem(.flexible(), spacing: 8)
                        ], spacing: 10) {
                            GridCard(emoji: "🕐", title: "最近播放", subtitle: "\(store.recent.count) 个项目")
                                .onTapGesture { path.append(NavDestination.recent) }
                            GridCard(emoji: "🎬", title: "本地视频", subtitle: "\(store.countKind(.video)) 个视频")
                                .onTapGesture { path.append(NavDestination.library(.video, "")) }
                            GridCard(emoji: "🖼️", title: "本地图片", subtitle: "\(store.countKind(.image)) 张图片")
                                .onTapGesture { path.append(NavDestination.library(.image, "")) }
                            GridCard(emoji: "📋", title: "播放列表", subtitle: "\(store.library.count) 个已导入")
                                .onTapGesture { path.append(NavDestination.playlist) }
                            GridCard(emoji: "📂", title: "打开媒体", subtitle: "视频、图片或 .mqp")
                                .onTapGesture { showFileImporter = true }
                            GridCard(emoji: "⚙️", title: "设置", subtitle: "播放、缓存和关于")
                                .onTapGesture { path.append(NavDestination.settings) }
                        }
                        
                        // 最近播放横滑
                        if !store.recent.isEmpty {
                            Text("继续播放")
                                .font(.subheadline.bold())
                                .foregroundColor(Color(hex: 0x9fa7b2))
                                .padding(.top, 18)
                                .padding(.bottom, 8)
                            
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(Array(store.recent.prefix(8).enumerated()), id: \.offset) { index, item in
                                        RecentCard(item: item)
                                            .onTapGesture {
                                                openItem(item)
                                            }
                                    }
                                }
                                .padding(.trailing, 18)
                            }
                        }
                    }
                    .padding(18)
                }
            }
            .navigationBarHidden(true)
            .navigationDestination(for: NavDestination.self) { dest in
                switch dest {
                case .recent:
                    RecentView(path: $path)
                case .library(let filter, let query):
                    LibraryView(path: $path, initialFilter: filter, initialQuery: query)
                case .playlist:
                    PlaylistView(path: $path)
                case .settings:
                    SettingsView(path: $path)
                case .packageDetail(let item):
                    PackageDetailView(item: item, path: $path)
                case .advancedTools:
                    AdvancedToolsView(path: $path)
                case .packageOptions(let urlStrings):
                    let urls = urlStrings.components(separatedBy: "\n").compactMap { URL(string: $0) }
                    PackageOptionsView(urls: urls, path: $path)
                }
            }
            .fullScreenCover(item: $videoItem) { item in
                VideoPlayerView(item: item, playlist: [item], initialIndex: 0)
                    .environmentObject(store)
            }
            .fullScreenCover(item: $imageSet) { set in
                ImageViewerView(items: set.items, initialIndex: 0)
                    .environmentObject(store)
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.movie, .video, .image, .mpeg4Movie, .quickTimeMovie, .data],
                allowsMultipleSelection: false
            ) { result in
                if case let .success(urls) = result, let url = urls.first {
                    handleOpenedFile(url)
                }
            }
        }
    }
    
    @State private var videoItem: MediaItem?
    @State private var imageSet: ImageSet?
    
    private func openItem(_ item: MediaItem) {
        store.addToRecent(item)
        switch item.kind {
        case .video:
            videoItem = item
        case .image:
            imageSet = ImageSet(items: [item])
        case .mpackage:
            path.append(NavDestination.packageDetail(item))
        default:
            break
        }
    }
    
    private func handleOpenedFile(_ url: URL) {
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }
        
        let name = url.lastPathComponent
        let kind = classifyKind(fileName: name, mimeType: "")
        
        if kind == .mpackage {
            // Handle .mqp
            Task {
                do {
                    let items = try MQPPackage.unpack(fileURL: url)
                    if !items.isEmpty {
                        store.addAllToLibrary(items)
                        await MainActor.run {
                            if let first = items.first {
                                store.addToRecent(first)
                                if first.kind == .video { videoItem = first }
                                else if first.kind == .image { imageSet = ImageSet(items: items) }
                            }
                        }
                    }
                } catch {
                    print("Failed to unpack: \(error)")
                }
            }
        } else {
            let item = MediaItem(
                uri: url.absoluteString,
                name: name,
                mimeType: UTType(filenameExtension: fileNameExtension(name))?.preferredMIMEType ?? "",
                kind: kind,
                source: "已导入"
            )
            store.addToLibrary(item)
            openItem(item)
        }
    }
}

// MARK: - Navigation Destinations
enum NavDestination: Hashable {
    case recent
    case library(FilterMode, String)
    case playlist
    case settings
    case packageDetail(MediaItem)
    case advancedTools
    case packageOptions(String) // stores comma-separated URL strings
    
    static func == (lhs: NavDestination, rhs: NavDestination) -> Bool {
        switch (lhs, rhs) {
        case (.recent, .recent): return true
        case (.library(let a, let b), .library(let c, let d)): return a == c && b == d
        case (.playlist, .playlist): return true
        case (.settings, .settings): return true
        case (.packageDetail(let a), .packageDetail(let b)): return a == b
        case (.advancedTools, .advancedTools): return true
        case (.packageOptions(let a), .packageOptions(let b)): return a == b
        default: return false
        }
    }
    
    func hash(into hasher: inout Hasher) {
        switch self {
        case .recent: hasher.combine(0)
        case .library(let f, let q): hasher.combine(1); hasher.combine(f); hasher.combine(q)
        case .playlist: hasher.combine(2)
        case .settings: hasher.combine(3)
        case .packageDetail(let i): hasher.combine(4); hasher.combine(i.uri)
        case .advancedTools: hasher.combine(5)
        case .packageOptions(let u): hasher.combine(6); hasher.combine(u)
        }
    }
}

// MARK: - Reusable Components
struct SearchBar: View {
    @Binding var text: String
    let placeholder: String
    var onCommit: (() -> Void)?
    
    var body: some View {
        HStack(spacing: 8) {
            Text("🔍")
                .font(.system(size: 16))
            TextField(placeholder, text: $text, onCommit: { onCommit?() })
                .font(.system(size: 15))
                .foregroundColor(.white)
                .accentColor(.white)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color(hex: 0x2c2d30))
        .cornerRadius(22)
    }
}

struct GridCard: View {
    let emoji: String
    let title: String
    let subtitle: String
    
    var body: some View {
        VStack(spacing: 6) {
            Text(emoji)
                .font(.system(size: 24))
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
            Text(subtitle)
                .font(.system(size: 11))
                .foregroundColor(Color(hex: 0xaeb4bd))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .padding(.horizontal, 14)
        .background(Color(hex: 0x212224))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(hex: 0x2d3036), lineWidth: 1)
        )
    }
}

struct RecentCard: View {
    let item: MediaItem
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(hex: 0x2c2d30))
                .frame(width: 140, height: 80)
                .overlay(
                    Text(item.kind.emoji)
                        .font(.system(size: 28))
                )
            Text(item.name)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(1)
        }
        .frame(width: 140)
        .padding(12)
        .background(Color(hex: 0x212224))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color(hex: 0x2d3036), lineWidth: 1)
        )
    }
}

struct SectionHeader: View {
    let label: String
    
    var body: some View {
        Text(label)
            .font(.subheadline.bold())
            .foregroundColor(Color(hex: 0x9fa7b2))
            .padding(.top, 18)
            .padding(.bottom, 8)
    }
}

struct NavBar: View {
    let title: String
    let back: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            Button(action: back) {
                Image(systemName: "chevron.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
            }
            Text(title)
                .font(.title2.bold())
                .foregroundColor(.white)
            Spacer()
        }
        .padding(.vertical, 6)
    }
}

struct ChipButton: View {
    let label: String
    let isActive: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(isActive ? .white : Color(hex: 0xaeb4bd))
                .padding(.horizontal, 18)
                .padding(.vertical, 6)
                .background(isActive ? AppTheme.accent : Color(hex: 0x2c2d30))
                .cornerRadius(18)
        }
    }
}

struct MediaRow: View {
    let item: MediaItem
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(hex: 0x2c2d30))
                    .frame(width: 48, height: 48)
                    .overlay(Text(item.kind.emoji).font(.system(size: 22)))
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text("\(item.kind.label) · \(item.source)")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xaeb4bd))
                        .lineLimit(1)
                }
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(hex: 0x212224))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(hex: 0x2a2d33), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

struct EmptyState: View {
    let message: String
    
    var body: some View {
        Text(message)
            .font(.system(size: 16))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 46)
    }
}

struct SettingCard: View {
    let title: String
    let subtitle: String
    let action: (() -> Void)?
    
    init(title: String, subtitle: String, action: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.action = action
    }
    
    var body: some View {
        Button {
            action?()
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: 0xaeb4bd))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color(hex: 0x212224))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(hex: 0x2d3036), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
    }
}

struct PrimaryButton: View {
    let label: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(AppTheme.accent)
                .cornerRadius(10)
        }
    }
}

struct GhostButton: View {
    let label: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color(hex: 0x2c2d30))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color(hex: 0x3a3b3e), lineWidth: 1)
                )
        }
    }
}
