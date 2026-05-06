import Foundation
import SwiftUI

// MARK: - MediaStore
class MediaStore: ObservableObject {
    static let shared = MediaStore()
    
    private let libraryKey = "library"
    private let recentKey = "recent"
    private let themeKey = "theme"
    
    @Published var library: [MediaItem] = []
    @Published var recent: [MediaItem] = []
    @Published var isDarkTheme: Bool = true
    
    private var defaults: UserDefaults { .standard }
    
    init() {
        loadLibrary()
        loadRecent()
        isDarkTheme = defaults.string(forKey: themeKey) != "light"
    }
    
    // MARK: - Persistence
    func saveLibrary() {
        if let data = try? JSONEncoder().encode(library) {
            defaults.set(data, forKey: libraryKey)
        }
    }
    
    func saveRecent() {
        if let data = try? JSONEncoder().encode(recent) {
            defaults.set(data, forKey: recentKey)
        }
    }
    
    private func loadLibrary() {
        guard let data = defaults.data(forKey: libraryKey),
              let items = try? JSONDecoder().decode([MediaItem].self, from: data) else { return }
        library = items
    }
    
    private func loadRecent() {
        guard let data = defaults.data(forKey: recentKey),
              let items = try? JSONDecoder().decode([MediaItem].self, from: data) else { return }
        recent = items
    }
    
    // MARK: - Library operations
    func addToLibrary(_ item: MediaItem) {
        library.removeAll { $0.uri == item.uri }
        library.insert(item, at: 0)
        saveLibrary()
    }
    
    func addAllToLibrary(_ items: [MediaItem]) {
        for item in items {
            library.removeAll { $0.uri == item.uri }
            library.insert(item, at: 0)
        }
        saveLibrary()
    }
    
    func addToRecent(_ item: MediaItem) {
        recent.removeAll { $0.uri == item.uri }
        var updated = item.withTimestamp(Date())
        recent.insert(updated, at: 0)
        if recent.count > 50 { recent = Array(recent.prefix(50)) }
        saveRecent()
    }
    
    func clearRecent() {
        recent.removeAll()
        saveRecent()
    }
    
    func countKind(_ kind: MediaKind) -> Int {
        library.filter { $0.kind == kind }.count
    }
    
    func filtered(filter: FilterMode, query: String, sort: SortMode) -> [MediaItem] {
        let q = query.lowercased()
        var items = library.filter { item in
            let matchesFilter = filter == .all || filter.kind == item.kind
            let matchesQuery = q.isEmpty || item.name.lowercased().contains(q)
            return matchesFilter && matchesQuery
        }
        switch sort {
        case .name:
            items.sort { $0.name.lowercased() < $1.name.lowercased() }
        case .type:
            items.sort { $0.mimeType < $1.mimeType }
        case .time:
            items.sort { $0.timestamp > $1.timestamp }
        }
        return items
    }
    
    func toggleTheme() {
        isDarkTheme.toggle()
        defaults.set(isDarkTheme ? "dark" : "light", forKey: themeKey)
    }
    
    func clearCache() {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("media-packages")
        if let dir = cacheDir, FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.removeItem(at: dir)
        }
    }
    
    var colors: ThemeColors { ThemeColors(isDark: isDarkTheme) }
}
