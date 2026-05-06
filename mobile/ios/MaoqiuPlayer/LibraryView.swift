import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    let initialFilter: FilterMode
    let initialQuery: String
    
    @State private var filter: FilterMode = .all
    @State private var query: String = ""
    @State private var sort: SortMode = .time
    @State private var showFileImporter = false
    @State private var showPHPicker = false
    @State private var showScanAlert = false
    @State private var videoItem: MediaItem?
    @State private var imageSet: ImageSet?
    
    private var filtered: [MediaItem] {
        store.filtered(filter: filter, query: query, sort: sort)
    }
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: libraryTitle) { path.removeLast() }
                    
                    // 搜索栏
                    SearchBar(text: $query, placeholder: "搜索名称")
                    
                    // 胶囊标签
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(FilterMode.allCases, id: \.self) { f in
                                ChipButton(label: f.label, isActive: f == filter) {
                                    filter = f
                                }
                            }
                        }
                        .padding(.top, 12)
                    }
                    
                    // 操作按钮
                    HStack(spacing: 10) {
                        PrimaryButton(label: "打开媒体") { showFileImporter = true }
                        GhostButton(label: "导入") { showPHPicker = true }
                        GhostButton(label: "扫描本机") { showScanAlert = true }
                    }
                    .padding(.top, 6)
                    
                    // 排序
                    HStack(spacing: 8) {
                        Text("排序：")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: 0xaeb4bd))
                        Picker("排序", selection: $sort) {
                            ForEach(SortMode.allCases, id: \.self) { s in
                                Text(s.label).tag(s)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(.top, 8)
                    
                    // 列表
                    if filtered.isEmpty {
                        EmptyState(message: "没有找到媒体")
                    } else {
                        ForEach(filtered) { item in
                            MediaRow(item: item) { openItem(item) }
                                .padding(.top, 8)
                        }
                    }
                }
                .padding(18)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            filter = initialFilter
            query = initialQuery
        }
        .fullScreenCover(item: $videoItem) { item in
            VideoPlayerView(item: item, playlist: filtered.filter { $0.kind == .video }, initialIndex: 0)
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
                handleFile(url)
            }
        }
        .sheet(isPresented: $showPHPicker) {
            MediaPickerView(selection: nil) { urls in
                for url in urls {
                    handleFile(url)
                }
            }
        }
        .alert("扫描本机媒体", isPresented: $showScanAlert) {
            Button("取消", role: .cancel) {}
            Button("扫描") {
                scanLocalMedia()
            }
        } message: {
            Text("将扫描本机相册中的视频和图片并导入媒体库")
        }
    }
    
    private var libraryTitle: String {
        switch filter {
        case .all: return "媒体库"
        case .video: return "本地视频"
        case .image: return "本地图片"
        case .mpackage: return "媒体包管理"
        }
    }
    
    private func openItem(_ item: MediaItem) {
        store.addToRecent(item)
        switch item.kind {
        case .video: videoItem = item
        case .image:
            let images = filtered.filter { $0.kind == .image }
            let idx = images.firstIndex(of: item) ?? 0
            let imageList = images.isEmpty ? [item] : images
            imageSet = ImageSet(items: imageList)
        case .mpackage: path.append(NavDestination.packageDetail(item))
        default: break
        }
    }
    
    private func handleFile(_ url: URL) {
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }
        
        let name = url.lastPathComponent
        let kind = classifyKind(fileName: name, mimeType: "")
        
        if kind == .mpackage {
            Task {
                do {
                    let items = try MQPPackage.unpack(fileURL: url)
                    store.addAllToLibrary(items)
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
    
    private func scanLocalMedia() {
        // Use PHAsset to enumerate local media
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            guard status == .authorized || status == .limited else { return }
            
            var scannedItems: [MediaItem] = []
            
            // Videos
            let videoOptions = PHFetchOptions()
            videoOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
            let videos = PHAsset.fetchAssets(with: .video, options: videoOptions)
            videos.enumerateObjects { asset, _, _ in
                let item = MediaItem(
                    uri: "ph://\(asset.localIdentifier)",
                    name: "Video-\(asset.localIdentifier.prefix(8))",
                    mimeType: "video/mp4",
                    kind: .video,
                    source: "本机媒体",
                    timestamp: asset.creationDate ?? Date()
                )
                scannedItems.append(item)
            }
            
            // Images
            let imageOptions = PHFetchOptions()
            imageOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
            let images = PHAsset.fetchAssets(with: .image, options: imageOptions)
            images.enumerateObjects { asset, _, _ in
                let item = MediaItem(
                    uri: "ph://\(asset.localIdentifier)",
                    name: "Image-\(asset.localIdentifier.prefix(8))",
                    mimeType: "image/jpeg",
                    kind: .image,
                    source: "本机媒体",
                    timestamp: asset.creationDate ?? Date()
                )
                scannedItems.append(item)
            }
            
            DispatchQueue.main.async {
                store.addAllToLibrary(scannedItems)
            }
        }
    }
}
