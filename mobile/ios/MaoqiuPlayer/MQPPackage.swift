import Foundation
import UniformTypeIdentifiers
import Compression

// MARK: - Media Package (打包/解包 .mqp)
enum MQPPackage {
    static let magic = "MAOQIU_PLAYER_ENC_V1"
    static let format = "MaoqiuPlayerMediaPackage"
    
    // MARK: - Unpack
    static func unpack(fileURL: URL) throws -> [MediaItem] {
        let data = try Data(contentsOf: fileURL)
        let magicData = Data(magic.utf8)
        
        guard data.count > magicData.count + 4 else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        
        // Verify magic
        let fileMagic = data.prefix(magicData.count)
        guard fileMagic == magicData else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        
        // Read header length
        let headerLengthOffset = magicData.count
        let headerLength: Int = data[headerLengthOffset..<headerLengthOffset+4].withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
        
        let headerOffset = headerLengthOffset + 4
        guard headerOffset + headerLength <= data.count else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        
        let headerData = data[headerOffset..<headerOffset+headerLength]
        guard let header = try? JSONSerialization.jsonObject(with: headerData) as? [String: Any] else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        
        guard header["format"] as? String == format else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        
        let payloadOffset = headerOffset + headerLength
        let encryptedPayload = data[payloadOffset...]
        
        // Verify checksum
        let expectedSha = header["payload_sha256"] as? String ?? ""
        if !expectedSha.isEmpty {
            let actualSha = CryptoUtil.sha256(Data(encryptedPayload))
            guard actualSha == expectedSha else {
                throw CryptoUtil.CryptoError.checksumFailed
            }
        }
        
        // Decrypt
        let salt = CryptoUtil.hexToBytes(header["salt"] as! String)
        let nonce = CryptoUtil.hexToBytes(header["nonce"] as! String)
        let iterations = header["iterations"] as? Int ?? 390000
        
        let zipData = try CryptoUtil.decryptPayload(Data(encryptedPayload), salt: salt, nonce: nonce, iterations: iterations)
        
        // Unzip to temp directory
        let tempDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("media-packages")
            .appendingPathComponent(sanitizeFileName(fileURL.deletingPathExtension().lastPathComponent))
        
        try? FileManager.default.removeItem(at: tempDir)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        
        let extracted = try unzipData(zipData, to: tempDir)
        return extracted
    }
    
    // MARK: - Pack
    static func pack(items: [(url: URL, name: String)], packageName: String, suffix: String = ".mqp") throws -> URL {
        // Create zip data
        var zipEntries: [(name: String, data: Data)] = []
        var usedNames = Set<String>()
        
        for (index, item) in items.enumerated() {
            let data = try Data(contentsOf: item.url)
            let arcName = uniqueArchiveName(item.name, usedNames: &usedNames, index: index + 1)
            usedNames.insert(arcName)
            zipEntries.append((name: arcName, data: data))
        }
        
        let zipData = try createZipData(entries: zipEntries)
        
        // Encrypt
        var salt = Data(count: 16)
        var nonce = Data(count: 12)
        _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        _ = nonce.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }
        
        let encryptedPayload = try CryptoUtil.encryptPayload(zipData, salt: salt, nonce: nonce)
        
        // Build header
        var itemsArray: [[String: Any]] = []
        for (index, item) in items.enumerated() {
            let arcName = Array(usedNames)[index % usedNames.count]
            itemsArray.append([
                "name": item.name,
                "media_type": classifyKind(fileName: item.name, mimeType: "").rawValue == "video" ? "video" : "image",
                "path": arcName
            ])
        }
        
        let header: [String: Any] = [
            "format": format,
            "version": "1",
            "app": "MaoqiuPlayer",
            "package_name": packageName,
            "created_at": ISO8601DateFormatter().string(from: Date()),
            "item_count": items.count,
            "items": itemsArray,
            "suffix": suffix,
            "cipher": "AES-256-GCM",
            "kdf": "PBKDF2-HMAC-SHA256",
            "iterations": 390000,
            "salt": CryptoUtil.bytesToHex(salt),
            "nonce": CryptoUtil.bytesToHex(nonce),
            "payload_sha256": CryptoUtil.sha256(encryptedPayload)
        ]
        
        let headerData = try JSONSerialization.data(withJSONObject: header)
        let magicData = Data(magic.utf8)
        
        // Assemble file
        var fileData = Data()
        fileData.append(magicData)
        var headerLength = UInt32(headerData.count).bigEndian
        fileData.append(Data(bytes: &headerLength, count: 4))
        fileData.append(headerData)
        fileData.append(encryptedPayload)
        
        // Save to Documents
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let outputURL = docs.appendingPathComponent(packageName + suffix)
        try fileData.write(to: outputURL)
        
        return outputURL
    }
    
    // MARK: - Helpers
    private static func unzipData(_ data: Data, to directory: URL) throws -> [MediaItem] {
        var items: [MediaItem] = []
        
        // Use Process to unzip (iOS doesn't have ZipArchive natively, use command line)
        let zipPath = directory.appendingPathComponent("_temp.zip")
        try data.write(to: zipPath)
        
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/unzip")
        process.arguments = ["-o", zipPath.path, "-d", directory.path]
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        process.waitUntilExit()
        
        try? FileManager.default.removeItem(at: zipPath)
        
        guard let enumerator = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: nil) else {
            return items
        }
        
        for case let fileURL as URL in enumerator {
            guard !fileURL.hasDirectoryPath else { continue }
            let name = fileURL.lastPathComponent
            guard name != "_temp.zip" else { continue }
            let kind = classifyKind(fileName: name, mimeType: "")
            if kind == .video || kind == .image {
                let item = MediaItem(
                    uri: fileURL.absoluteString,
                    name: name,
                    mimeType: UTType(filenameExtension: fileNameExtension(name))?.preferredMIMEType ?? "",
                    kind: kind,
                    source: "媒体包"
                )
                items.append(item)
            }
        }
        
        return items
    }
    
    private static func createZipData(entries: [(name: String, data: Data)]) throws -> Data {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }
        
        for entry in entries {
            let fileURL = tempDir.appendingPathComponent(entry.name)
            try entry.data.write(to: fileURL)
        }
        
        let zipURL = tempDir.appendingPathComponent("archive.zip")
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/zip")
        process.arguments = ["-j", zipURL.path] + entries.map(\.name)
        process.currentDirectoryURL = tempDir
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        process.waitUntilExit()
        
        return try Data(contentsOf: zipURL)
    }
    
    private static func uniqueArchiveName(_ name: String, usedNames: inout Set<String>, index: Int) -> String {
        if !usedNames.contains(name) { return name }
        let ext = (name as NSString).pathExtension
        let base = (name as NSString).deletingPathExtension
        if ext.isEmpty { return "\(base)-\(index)" }
        return "\(base)-\(index).\(ext)"
    }
}
