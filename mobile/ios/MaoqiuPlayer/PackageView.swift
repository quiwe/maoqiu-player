import SwiftUI

struct PackageDetailView: View {
    let item: MediaItem
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    @State private var isLoading = false
    @State private var showError = false
    @State private var errorMessage = ""
    @State private var videoItem: MediaItem?
    @State private var imageSet: ImageSet?
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: item.name) {
                        path.removeLast()
                    }
                    
                    SectionHeader(label: "媒体包")
                    
                    SettingCard(title: "私有媒体包", subtitle: "可在应用内打开并临时播放包内媒体")
                    
                    if isLoading {
                        HStack {
                            Spacer()
                            VStack(spacing: 12) {
                                ProgressView()
                                    .tint(AppTheme.accent)
                                Text("正在打开媒体包...")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .padding(.top, 80)
                            Spacer()
                        }
                    } else {
                        PrimaryButton(label: "打开媒体包") {
                            extractAndPlay()
                        }
                        .padding(.top, 12)
                    }
                }
                .padding(18)
            }
        }
        .navigationBarHidden(true)
        .fullScreenCover(item: $videoItem) { item in
            VideoPlayerView(item: item, playlist: [item], initialIndex: 0)
                .environmentObject(store)
        }
        .fullScreenCover(item: $imageSet) { set in
            ImageViewerView(items: set.items, initialIndex: 0)
                .environmentObject(store)
        }
        .alert("打开失败", isPresented: $showError) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
    }
    
    private func extractAndPlay() {
        guard let url = URL(string: item.uri) else {
            errorMessage = "无效的媒体包地址"
            showError = true
            return
        }
        
        isLoading = true
        
        Task {
            do {
                let extracted = try MQPPackage.unpack(fileURL: url)
                await MainActor.run {
                    isLoading = false
                    if extracted.isEmpty {
                        errorMessage = "媒体包中没有可播放项目"
                        showError = true
                        return
                    }
                    store.addAllToLibrary(extracted)
                    if let first = extracted.first {
                        store.addToRecent(first)
                        switch first.kind {
                        case .video: videoItem = first
                        case .image: imageSet = ImageSet(items: extracted)
                        default: break
                        }
                    }
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    errorMessage = error.localizedDescription
                    showError = true
                }
            }
        }
    }
}

struct PackageOptionsView: View {
    let urls: [URL]
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    
    @State private var packageName = ""
    @State private var customPath = ""
    @State private var customSuffix = ".mqp"
    @State private var isPacking = false
    @State private var packResult = ""
    @State private var showResult = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: "打包媒体") { path.removeLast() }
                    
                    Text("已选择 \(urls.count) 个文件")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: 0xaeb4bd))
                        .padding(.top, 8)
                    
                    SectionHeader(label: "文件名称（留空自动生成）")
                    TextField("例如: 我的旅行合集", text: $packageName)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(14)
                        .background(Color(hex: 0x2c2d30))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: 0x3a3b3e), lineWidth: 1)
                        )
                    
                    SectionHeader(label: "保存路径（留空默认 Documents）")
                    HStack {
                        TextField("例如: MyPackages", text: $customPath)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .padding(14)
                            .background(Color(hex: 0x2c2d30))
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color(hex: 0x3a3b3e), lineWidth: 1)
                            )
                    }
                    
                    SectionHeader(label: "文件后缀（留空默认 .mqp）")
                    TextField(".mqp", text: $customSuffix)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(14)
                        .background(Color(hex: 0x2c2d30))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: 0x3a3b3e), lineWidth: 1)
                        )
                    
                    if isPacking {
                        HStack {
                            Spacer()
                            VStack(spacing: 12) {
                                ProgressView()
                                    .tint(AppTheme.accent)
                                Text("正在打包 \(urls.count) 个媒体...")
                                    .font(.system(size: 15))
                                    .foregroundColor(.white)
                            }
                            .padding(.top, 24)
                            Spacer()
                        }
                    } else {
                        PrimaryButton(label: "打包") {
                            startPacking()
                        }
                        .padding(.top, 24)
                    }
                }
                .padding(18)
            }
        }
        .navigationBarHidden(true)
        .alert("打包完成", isPresented: $showResult) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(packResult)
        }
        .alert("打包失败", isPresented: $showError) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
    }
    
    private func startPacking() {
        isPacking = true
        
        Task {
            do {
                var suffix = customSuffix.trimmingCharacters(in: .whitespaces)
                if suffix.isEmpty { suffix = ".mqp" }
                if !suffix.hasPrefix(".") { suffix = "." + suffix }
                
                let name = packageName.trimmingCharacters(in: .whitespaces)
                let baseName = name.isEmpty ? "media-pack-\(Int(Date().timeIntervalSince1970))" : name
                
                let items: [(url: URL, name: String)] = urls.map { url in
                    (url: url, name: url.lastPathComponent)
                }
                
                let outputURL = try MQPPackage.pack(items: items, packageName: baseName, suffix: suffix)
                
                // Add to library
                let item = MediaItem(
                    uri: outputURL.absoluteString,
                    name: baseName + suffix,
                    mimeType: "application/octet-stream",
                    kind: .mpackage,
                    source: "已打包"
                )
                store.addToLibrary(item)
                
                await MainActor.run {
                    isPacking = false
                    packResult = "打包完成: \(baseName + suffix)"
                    showResult = true
                }
            } catch {
                await MainActor.run {
                    isPacking = false
                    errorMessage = "打包失败: \(error.localizedDescription)"
                    showError = true
                }
            }
        }
    }
}
