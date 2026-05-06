import Foundation

// MARK: - Media Kind
enum MediaKind: String, Codable, CaseIterable {
    case video, image, mpackage, unknown
    
    var label: String {
        switch self {
        case .video: return "视频"
        case .image: return "图片"
        case .mpackage: return "媒体包"
        case .unknown: return "文件"
        }
    }
    
    var emoji: String {
        switch self {
        case .video: return "🎬"
        case .image: return "🖼️"
        case .mpackage: return "📦"
        case .unknown: return "📄"
        }
    }
}

// MARK: - Sort
enum SortMode: String, CaseIterable {
    case time, name, type
    var label: String {
        switch self {
        case .time: return "最近"
        case .name: return "名称"
        case .type: return "格式"
        }
    }
}

// MARK: - Filter
enum FilterMode: String, CaseIterable {
    case all, video, image, mpackage
    var label: String {
        switch self {
        case .all: return "全部"
        case .video: return "视频"
        case .image: return "图片"
        case .mpackage: return "媒体包"
        }
    }
    var kind: MediaKind? {
        switch self {
        case .all: return nil
        case .video: return .video
        case .image: return .image
        case .mpackage: return .mpackage
        }
    }
}

// MARK: - MediaItem
struct MediaItem: Codable, Identifiable, Equatable {
    var id: String { uri }
    let uri: String
    let name: String
    let mimeType: String
    let kind: MediaKind
    let source: String
    var timestamp: Date
    
    init(uri: String, name: String?, mimeType: String?, kind: MediaKind?, source: String?, timestamp: Date? = nil) {
        self.uri = uri
        self.name = name ?? "未命名媒体"
        self.mimeType = mimeType ?? ""
        self.kind = kind ?? .unknown
        self.source = source ?? "媒体库"
        self.timestamp = timestamp ?? Date()
    }
    
    func withTimestamp(_ value: Date) -> MediaItem {
        MediaItem(uri: uri, name: name, mimeType: mimeType, kind: kind, source: source, timestamp: value)
    }
}

// MARK: - Helpers
func classifyKind(fileName: String, mimeType: String) -> MediaKind {
    let lower = fileName.lowercased()
    if lower.hasSuffix(".mqp") { return .mpackage }
    if mimeType.hasPrefix("video/") { return .video }
    if mimeType.hasPrefix("image/") { return .image }
    if lower.range(of: #"\.(mp4|m4v|mov|mkv|webm|avi|3gp)$"#, options: .regularExpression) != nil { return .video }
    if lower.range(of: #"\.(jpg|jpeg|png|webp|gif|bmp|heic|heif)$"#, options: .regularExpression) != nil { return .image }
    return .unknown
}

func fileNameExtension(_ name: String) -> String {
    (name as NSString).pathExtension.lowercased()
}

func sanitizeFileName(_ value: String) -> String {
    value.replacingOccurrences(of: "[^a-zA-Z0-9._-]", with: "_", options: .regularExpression)
}
