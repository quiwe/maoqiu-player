import SwiftUI
import AVKit

// MARK: - Video Player (AVPlayer + 半透明悬浮控制层)
struct VideoPlayerView: View {
    let item: MediaItem
    let playlist: [MediaItem]
    let initialIndex: Int
    @EnvironmentObject var store: MediaStore
    @Environment(\.dismiss) var dismiss
    
    @State private var player: AVPlayer?
    @State private var isPlaying = true
    @State private var showControls = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var isSeeking = false
    @State private var hideTask: DispatchWorkItem?
    @State private var currentIndex: Int = 0
    @State private var timeObserver: Any?
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if let player = player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        toggleControls()
                    }
            } else {
                ProgressView("加载中...")
                    .foregroundColor(.white)
            }
            
            // Top overlay
            if showControls {
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
                    .background(Color.black.opacity(0.6))
                    
                    Spacer()
                    
                    // Bottom overlay
                    HStack(spacing: 8) {
                        Button {
                            openNeighbor(-1)
                        } label: {
                            Image(systemName: "backward.fill")
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                        }
                        .disabled(playlist.count < 2)

                        Button {
                            togglePlayback()
                        } label: {
                            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                        }

                        Button {
                            openNeighbor(1)
                        } label: {
                            Image(systemName: "forward.fill")
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                        }
                        .disabled(playlist.count < 2)

                        Text(formatTime(currentTime))
                            .font(.caption)
                            .foregroundColor(.white)
                            .frame(width: 48, alignment: .leading)
                        
                        Slider(value: $currentTime, in: 0...max(duration, 1)) { editing in
                            isSeeking = editing
                            if !editing {
                                player?.seek(to: CMTime(seconds: currentTime, preferredTimescale: 600))
                            }
                        }
                        .accentColor(AppTheme.accent)
                        
                        Text(formatTime(duration))
                            .font(.caption)
                            .foregroundColor(.white)
                            .frame(width: 48, alignment: .trailing)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.6))
                }
            }
        }
        .onAppear {
            currentIndex = initialIndex
            loadVideo()
            startHideTimer()
        }
        .onDisappear {
            if let timeObserver {
                player?.removeTimeObserver(timeObserver)
                self.timeObserver = nil
            }
            player?.pause()
            player = nil
        }
        .statusBarHidden(!showControls)
    }
    
    private var currentItem: MediaItem {
        guard currentIndex >= 0 && currentIndex < playlist.count else { return item }
        return playlist[currentIndex]
    }
    
    private func loadVideo() {
        let current = currentItem
        guard let url = URL(string: current.uri) else { return }
        if let timeObserver {
            player?.removeTimeObserver(timeObserver)
            self.timeObserver = nil
        }
        let avPlayer = AVPlayer(url: url)
        self.player = avPlayer
        currentTime = 0
        duration = 0
        
        // Observe duration
        Task {
            if let item = avPlayer.currentItem {
                let dur = try? await item.asset.load(.duration)
                if let dur = dur {
                    await MainActor.run {
                        self.duration = CMTimeGetSeconds(dur)
                    }
                }
            }
        }
        
        // Observe time
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
            Task { @MainActor in
                guard !isSeeking else { return }
                currentTime = CMTimeGetSeconds(time)
            }
        }
        
        avPlayer.play()
        isPlaying = true
    }
    
    private func toggleControls() {
        if showControls {
            showControls = false
        } else {
            showControls = true
            startHideTimer()
        }
    }

    private func togglePlayback() {
        if isPlaying {
            player?.pause()
            isPlaying = false
        } else {
            player?.play()
            isPlaying = true
            startHideTimer()
        }
    }

    private func openNeighbor(_ offset: Int) {
        guard !playlist.isEmpty else { return }
        currentIndex = (currentIndex + offset + playlist.count) % playlist.count
        player?.pause()
        loadVideo()
        startHideTimer()
    }
    
    private func startHideTimer() {
        hideTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation { showControls = false }
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3, execute: task)
    }
    
    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && !seconds.isInfinite else { return "00:00" }
        let total = Int(seconds)
        let min = total / 60
        let sec = total % 60
        return String(format: "%02d:%02d", min, sec)
    }
}
