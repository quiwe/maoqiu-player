import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

// MARK: - Photo/Video Picker (PHPicker)
struct MediaPickerView: UIViewControllerRepresentable {
    let selection: PHPickerFilter?
    let onPick: ([URL]) -> Void
    
    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = selection
        config.selectionLimit = 0 // unlimited
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }
    
    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }
    
    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onPick: ([URL]) -> Void
        init(onPick: @escaping ([URL]) -> Void) { self.onPick = onPick }
        
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            
            guard !results.isEmpty else { return }
            var urls: [URL] = []
            let group = DispatchGroup()
            
            for result in results {
                group.enter()
                let provider = result.itemProvider
                if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                    provider.loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier) { url, error in
                        defer { group.leave() }
                        guard let url = url else { return }
                        // Copy to temp directory (original URL gets deleted)
                        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                        try? FileManager.default.removeItem(at: tempURL)
                        try? FileManager.default.copyItem(at: url, to: tempURL)
                        urls.append(tempURL)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, error in
                        defer { group.leave() }
                        guard let url = url else { return }
                        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                        try? FileManager.default.removeItem(at: tempURL)
                        try? FileManager.default.copyItem(at: url, to: tempURL)
                        urls.append(tempURL)
                    }
                } else {
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                self.onPick(urls)
            }
        }
    }
}

// MARK: - Document Picker
struct DocumentPickerView: UIViewControllerRepresentable {
    let allowedTypes: [UTType]
    let allowsMultiple: Bool
    let onPick: ([URL]) -> Void
    
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: allowedTypes, asCopy: true)
        picker.allowsMultipleSelection = allowsMultiple
        picker.delegate = context.coordinator
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }
    
    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: ([URL]) -> Void
        init(onPick: @escaping ([URL]) -> Void) { self.onPick = onPick }
        
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            onPick(urls)
        }
    }
}
