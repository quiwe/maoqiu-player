import SwiftUI

@main
struct MaoqiuPlayerApp: App {
    @StateObject private var store = MediaStore.shared
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .preferredColorScheme(store.isDarkTheme ? .dark : .light)
        }
    }
}
