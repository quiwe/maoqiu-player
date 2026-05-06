import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var importingMedia = false
    @State private var selectedMediaName = "尚未打开媒体"

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.067, green: 0.071, blue: 0.078).ignoresSafeArea()
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("毛球播放器")
                            .font(.largeTitle.bold())
                            .foregroundStyle(.white)
                        Text("MaoqiuPlayer")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                    }

                    Button {
                        importingMedia = true
                    } label: {
                        Label("打开媒体", systemImage: "play.rectangle.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Text(selectedMediaName)
                        .foregroundStyle(.white.opacity(0.86))
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

                    Text("移动端初版用于打开本地视频和图片。媒体库、播放列表和媒体包功能会逐步补齐。")
                        .foregroundStyle(.secondary)

                    Spacer()
                }
                .padding(24)
            }
            .fileImporter(
                isPresented: $importingMedia,
                allowedContentTypes: [.movie, .video, .image, .mpeg4Movie, .quickTimeMovie],
                allowsMultipleSelection: false
            ) { result in
                if case let .success(urls) = result, let url = urls.first {
                    selectedMediaName = url.lastPathComponent
                }
            }
        }
    }
}
