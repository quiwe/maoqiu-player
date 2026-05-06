import SwiftUI

struct PlaylistView: View {
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    @State private var videoItem: MediaItem?
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
            }
        }
        .navigationBarHidden(true)
        .fullScreenCover(item: $videoItem) { item in
            VideoPlayerView(item: item, playlist: store.library.filter { $0.kind == .video }, initialIndex: 0)
                .environmentObject(store)
        }
        .fullScreenCover(item: $imageSet) { set in
            ImageViewerView(items: set.items, initialIndex: 0)
                .environmentObject(store)
        }
    }
    
    private func openItem(_ item: MediaItem) {
        store.addToRecent(item)
        switch item.kind {
        case .video: videoItem = item
        case .image: imageSet = ImageSet(items: [item])
        case .mpackage: path.append(NavDestination.packageDetail(item))
        default: break
        }
    }
}
