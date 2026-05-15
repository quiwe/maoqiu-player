import SwiftUI

// MARK: - Image Viewer (全屏 + 双指缩放 + 左右滑动)
struct ImageViewerView: View {
    let items: [MediaItem]
    let initialIndex: Int
    @EnvironmentObject var store: MediaStore
    @Environment(\.dismiss) var dismiss
    
    @State private var currentIndex: Int = 0
    @State private var showBar = true
    @State private var hideTask: DispatchWorkItem?
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if items.isEmpty {
                Text("没有图片")
                    .foregroundColor(.white)
            } else {
                TabView(selection: $currentIndex) {
                    ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                        ZoomableScrollView(
                            image: loadImage(from: item.uri)
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea()
            }
            
            // Top bar overlay
            if showBar {
                VStack {
                    HStack {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                        }
                        
                        Text(currentItem.name)
                            .font(.headline)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 10)
                    .background(
                        LinearGradient(
                            colors: [Color.black.opacity(0.6), Color.clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    
                    Spacer()
                }
            }
        }
        .onTapGesture(count: 1) {
            withAnimation(.easeInOut(duration: 0.2)) {
                showBar.toggle()
            }
            if showBar { startHideTimer() }
        }
        .onAppear {
            currentIndex = min(max(0, initialIndex), max(0, items.count - 1))
            startHideTimer()
        }
        .onChange(of: currentIndex) { _ in
            startHideTimer()
        }
    }
    
    private var currentItem: MediaItem {
        guard currentIndex >= 0 && currentIndex < items.count else {
            return MediaItem(uri: "", name: "图片查看", mimeType: "", kind: .image, source: "")
        }
        return items[currentIndex]
    }
    
    private func startHideTimer() {
        hideTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation { showBar = false }
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3, execute: task)
    }
    
    private func loadImage(from uri: String) -> UIImage? {
        guard let url = URL(string: uri) else { return nil }
        if url.isFileURL {
            return UIImage(contentsOfFile: url.path)
        }
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }
}

// MARK: - Zoomable Scroll View
struct ZoomableScrollView: UIViewRepresentable {
    let image: UIImage?
    
    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.delegate = context.coordinator
        scrollView.maximumZoomScale = 5.0
        scrollView.minimumZoomScale = 1.0
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.backgroundColor = .black
        
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.tag = 100
        imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrollView.addSubview(imageView)
        
        context.coordinator.imageView = imageView
        
        // Double tap to zoom
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scrollView.addGestureRecognizer(doubleTap)
        
        return scrollView
    }
    
    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        if let imageView = scrollView.viewWithTag(100) as? UIImageView {
            imageView.image = image
            imageView.frame = scrollView.bounds
            scrollView.contentSize = scrollView.bounds.size
            context.coordinator.centerImage(in: scrollView)
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    class Coordinator: NSObject, UIScrollViewDelegate {
        var imageView: UIImageView?
        
        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            scrollView.viewWithTag(100)
        }
        
        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            centerImage(in: scrollView)
        }

        func centerImage(in scrollView: UIScrollView) {
            guard let imageView = imageView else { return }
            let offsetX = max((scrollView.bounds.width - imageView.frame.width) / 2, 0)
            let offsetY = max((scrollView.bounds.height - imageView.frame.height) / 2, 0)
            imageView.center = CGPoint(
                x: scrollView.bounds.width / 2 + offsetX,
                y: scrollView.bounds.height / 2 + offsetY
            )
        }
        
        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = gesture.view as? UIScrollView else { return }
            if scrollView.zoomScale > scrollView.minimumZoomScale {
                scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
            } else {
                let point = gesture.location(in: scrollView)
                let rect = CGRect(x: point.x - 50, y: point.y - 50, width: 100, height: 100)
                scrollView.zoom(to: rect, animated: true)
            }
        }
    }
}
