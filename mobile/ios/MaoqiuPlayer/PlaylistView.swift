import SwiftUI

struct PlaylistView: View {
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    @State private var videoItem: MediaItem?
    @State private var videoPlaylist: [MediaItem] = []
    @State private var videoInitialIndex = 0
    @State private var imageSet: ImageSet?
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: "播放列表") { path.removeLast() }
                    
                    SectionHeader(label: "默认播放列表")
                    
                    if store.library.isEmpty {
                        EmptyState(message: "还没有导入媒体")
                    } else {
                        ForEach(store.library) { item in
                            MediaRow(item: item) { openItem(item) }
                                .padding(.top, 8)
                        }
                    }
                }
                .padding(18)
                .frame(maxWidth: 560, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .navigationBarHidden(true)
        .fullScreenCover(item: $videoItem) { item in
            VideoPlayerView(item: item, playlist: videoPlaylist.isEmpty ? [item] : videoPlaylist, initialIndex: videoInitialIndex)
                .environmentObject(store)
        }
        .fullScreenCover(item: $imageSet) { set in
            ImageViewerView(items: set.items, initialIndex: set.initialIndex)
                .environmentObject(store)
        }
    }
    
    private func openItem(_ item: MediaItem) {
        switch item.kind {
        case .video:
            let videos = store.library.filter { $0.kind == .video }
            videoPlaylist = videos.isEmpty ? [item] : videos
            videoInitialIndex = videoPlaylist.firstIndex(of: item) ?? 0
            videoItem = item
        case .image:
            let images = store.library.filter { $0.kind == .image }
            let idx = images.firstIndex(of: item) ?? 0
            imageSet = ImageSet(items: images.isEmpty ? [item] : images, initialIndex: idx)
        case .mpackage: path.append(NavDestination.packageDetail(item))
        default: break
        }
    }
}
