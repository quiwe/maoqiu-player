import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    @State private var showClearCacheAlert = false
    @State private var showClearRecentAlert = false
    @State private var cacheCleared = false
    
    private let appVersion = "0.1.14"
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: "设置") { path.removeLast() }
                    
                    // 常规
                    SectionHeader(label: "常规")
                    
                    // 主题切换
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("外观主题")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(.white)
                            Text(store.isDarkTheme ? "深色主题" : "浅色主题")
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: 0xaeb4bd))
                        }
                        Spacer()
                        Toggle("", isOn: Binding(
                            get: { !store.isDarkTheme },
                            set: { _ in store.toggleTheme() }
                        ))
                        .tint(AppTheme.accent)
                    }
                    .padding(14)
                    .background(Color(hex: 0x212224))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: 0x2d3036), lineWidth: 1)
                    )
                    
                    SettingCard(title: "播放设置", subtitle: "应用内播放、倍速和全屏控制")
                        .padding(.top, 10)
                    
                    SettingCard(title: "媒体库", subtitle: "\(store.library.count) 个本地媒体项目") {
                        path.append(NavDestination.library(.all, ""))
                    }
                    .padding(.top, 10)
                    
                    // 存储
                    SectionHeader(label: "存储")
                    
                    SettingCard(title: "缓存", subtitle: "清理媒体包临时文件") {
                        showClearCacheAlert = true
                    }
                    
                    // 高级
                    SectionHeader(label: "高级")
                    
                    SettingCard(title: "高级设置", subtitle: "媒体包管理、文件校验和数据库维护") {
                        path.append(NavDestination.advancedTools)
                    }
                    .padding(.top, 10)
                    
                    // 关于
                    SectionHeader(label: "关于")
                    
                    SettingCard(title: "关于毛球播放器", subtitle: "MaoqiuPlayer \(appVersion)")
                    
                    // 清空最近播放
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("清空最近播放")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(.white)
                            Text("删除所有播放记录")
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: 0xaeb4bd))
                        }
                        Spacer()
                        Toggle("", isOn: Binding(
                            get: { false },
                            set: { newValue in
                                if newValue { showClearRecentAlert = true }
                            }
                        ))
                        .tint(AppTheme.accent)
                    }
                    .padding(14)
                    .background(Color(hex: 0x212224))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: 0x2d3036), lineWidth: 1)
                    )
                    .padding(.top, 10)
                }
                .padding(18)
            }
        }
        .navigationBarHidden(true)
        .alert("清理缓存", isPresented: $showClearCacheAlert) {
            Button("取消", role: .cancel) {}
            Button("清理") {
                store.clearCache()
                cacheCleared = true
            }
        } message: {
            Text("将删除媒体包临时播放文件")
        }
        .alert("清空最近播放", isPresented: $showClearRecentAlert) {
            Button("取消", role: .cancel) {}
            Button("清空", role: .destructive) {
                store.clearRecent()
            }
        } message: {
            Text("确定要清空所有播放记录吗？")
        }
        .overlay {
            if cacheCleared {
                VStack {
                    Spacer()
                    Text("缓存已清理")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(20)
                        .padding(.bottom, 60)
                }
                .transition(.opacity)
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        withAnimation { cacheCleared = false }
                    }
                }
            }
        }
    }
}

struct AdvancedToolsView: View {
    @EnvironmentObject var store: MediaStore
    @Binding var path: NavigationPath
    @State private var showPackageMedia = false
    @State private var packageURLs: [URL] = []
    @State private var cacheCleared = false
    
    var body: some View {
        ZStack {
            store.colors.bg.ignoresSafeArea()
            
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    NavBar(title: "高级工具") { path.removeLast() }
                    
                    SettingCard(title: "媒体包管理", subtitle: "导入、查看和打开 .mqp 媒体包") {
                        path.append(NavDestination.library(.mpackage, ""))
                    }
                    
                    SettingCard(title: "清理缓存", subtitle: "删除媒体包临时播放文件") {
                        store.clearCache()
                        cacheCleared = true
                    }
                    .padding(.top, 10)
                    
                    SettingCard(title: "打包媒体", subtitle: "将视频和图片打包为 .mqp 媒体包") {
                        showPackageMedia = true
                    }
                    .padding(.top, 10)
                    
                    SettingCard(title: "数据库维护", subtitle: "重建本机媒体索引")
                        .padding(.top, 10)
                    
                    SettingCard(title: "文件校验", subtitle: "打开媒体包时会自动校验文件完整性")
                        .padding(.top, 10)
                }
                .padding(18)
            }
        }
        .navigationBarHidden(true)
        .fileImporter(
            isPresented: $showPackageMedia,
            allowedContentTypes: [.movie, .video, .image, .mpeg4Movie, .quickTimeMovie, .data],
            allowsMultipleSelection: true
        ) { result in
            if case let .success(urls) = result, !urls.isEmpty {
                packageURLs = urls
                let urlStrings = urls.map { $0.absoluteString }.joined(separator: "\n")
                path.append(NavDestination.packageOptions(urlStrings))
            }
        }
        .overlay {
            if cacheCleared {
                VStack {
                    Spacer()
                    Text("缓存已清理")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(20)
                        .padding(.bottom, 60)
                }
                .transition(.opacity)
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        withAnimation { cacheCleared = false }
                    }
                }
            }
        }
    }
}
