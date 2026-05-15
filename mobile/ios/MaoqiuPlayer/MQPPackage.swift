import Foundation
import UniformTypeIdentifiers
import Security
import ZIPFoundation

// MARK: - Media Package (打包/解包 .mqp)
enum MQPPackage {
    static let magic = "MAOQIU_PLAYER_ENC_V1"
    static let format = "MaoqiuPlayerMediaPackage"
    static let liteMagic = Data([0x4d, 0x41, 0x4f, 0x4c, 0x49, 0x54, 0x45, 0x31, 0x00])
    static let secureMagic = Data([0x4d, 0x41, 0x4f, 0x51, 0x49, 0x55, 0x31, 0x00])
    static let liteFormat = "MAOQIU_LITE"
    static let litePayloadFile = "file"
    static let litePayloadBundle = "tar_bundle"
    
    // MARK: - Unpack
    static func unpack(fileURL: URL) throws -> [MediaItem] {
        let data = try Data(contentsOf: fileURL)
        let magicData = Data(magic.utf8)

        if data.starts(with: magicData) {
            return try unpackPlayerPackage(data: data, fileURL: fileURL, magicData: magicData)
        }
        if data.starts(with: liteMagic) {
            return try unpackLitePackage(data: data, fileURL: fileURL)
        }
        if data.starts(with: secureMagic) {
            throw CryptoUtil.CryptoError.unsupportedSecurePackage
        }
        throw CryptoUtil.CryptoError.invalidPackage
    }

    static func isSupportedPackage(fileURL: URL) -> Bool {
        guard let handle = try? FileHandle(forReadingFrom: fileURL) else { return false }
        defer { try? handle.close() }
        let maxMagicLength = max(Data(magic.utf8).count, max(liteMagic.count, secureMagic.count))
        let prefix = handle.readData(ofLength: maxMagicLength)
        return prefix.starts(with: Data(magic.utf8)) || prefix.starts(with: liteMagic) || prefix.starts(with: secureMagic)
    }

    private static func unpackPlayerPackage(data: Data, fileURL: URL, magicData: Data) throws -> [MediaItem] {
        guard data.count > magicData.count + 4 else {
            throw CryptoUtil.CryptoError.invalidPackage
        }

        // Read header length
        let headerLengthOffset = magicData.count
        let headerLength = Int(data[headerLengthOffset..<headerLengthOffset + 4].reduce(UInt32(0)) { partial, byte in
            (partial << 8) | UInt32(byte)
        })
        
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
        guard let saltHex = header["salt"] as? String,
              let nonceHex = header["nonce"] as? String else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        let salt = CryptoUtil.hexToBytes(saltHex)
        let nonce = CryptoUtil.hexToBytes(nonceHex)
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

    private static func unpackLitePackage(data: Data, fileURL: URL) throws -> [MediaItem] {
        guard data.count > liteMagic.count + 4 else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        let headerLengthOffset = liteMagic.count
        let headerLength = Int(data[headerLengthOffset..<headerLengthOffset + 4].reduce(UInt32(0)) { partial, byte in
            (partial << 8) | UInt32(byte)
        })
        let headerOffset = headerLengthOffset + 4
        guard headerOffset + headerLength <= data.count else {
            throw CryptoUtil.CryptoError.invalidPackage
        }

        let headerData = data[headerOffset..<headerOffset + headerLength]
        guard let header = try? JSONSerialization.jsonObject(with: headerData) as? [String: Any],
              header["format"] as? String == liteFormat,
              (header["version"] as? Int ?? 0) == 1,
              let nonceText = header["payload_nonce"] as? String,
              let nonce = Data(base64Encoded: nonceText) else {
            throw CryptoUtil.CryptoError.invalidPackage
        }

        let payloadOffset = headerOffset + headerLength
        let encryptedPayload = Data(data[payloadOffset...])
        let plain = try CryptoUtil.decryptRawAESGCM(encryptedPayload, key: CryptoUtil.liteAppKey, nonce: nonce)
        let expectedSha = header["original_sha256"] as? String ?? ""
        if !expectedSha.isEmpty, CryptoUtil.sha256(plain) != expectedSha {
            throw CryptoUtil.CryptoError.checksumFailed
        }

        let tempDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("media-packages")
            .appendingPathComponent(sanitizeFileName(fileURL.deletingPathExtension().lastPathComponent))
        try? FileManager.default.removeItem(at: tempDir)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

        let payloadType = header["payload_type"] as? String ?? litePayloadFile
        if payloadType == litePayloadBundle {
            return try untarData(plain, to: tempDir)
        }

        let originalName = sanitizeFileName((header["original_filename"] as? String) ?? fileURL.lastPathComponent)
        let fileURL = tempDir.appendingPathComponent(originalName)
        try plain.write(to: fileURL)
        return mediaItems(for: [fileURL])
    }
    
    // MARK: - Pack
    static func pack(items: [(url: URL, name: String)], packageName: String, suffix: String = ".mqp", outputFolder: String = "") throws -> URL {
        // Create zip data
        var zipEntries: [(name: String, data: Data)] = []
        var usedNames = Set<String>()
        var archiveNames: [String] = []
        
        for (index, item) in items.enumerated() {
            let accessed = item.url.startAccessingSecurityScopedResource()
            defer {
                if accessed {
                    item.url.stopAccessingSecurityScopedResource()
                }
            }
            let data = try Data(contentsOf: item.url)
            let arcName = uniqueArchiveName(item.name, usedNames: &usedNames, index: index + 1)
            usedNames.insert(arcName)
            archiveNames.append(arcName)
            zipEntries.append((name: arcName, data: data))
        }
        
        let zipData = try createZipData(entries: zipEntries)
        
        // Encrypt
        var salt = Data(count: 16)
        var nonce = Data(count: 12)
        guard salt.withUnsafeMutableBytes({ SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }) == errSecSuccess,
              nonce.withUnsafeMutableBytes({ SecRandomCopyBytes(kSecRandomDefault, 12, $0.baseAddress!) }) == errSecSuccess else {
            throw CryptoUtil.CryptoError.encryptionFailed
        }
        
        let encryptedPayload = try CryptoUtil.encryptPayload(zipData, salt: salt, nonce: nonce)
        
        // Build header
        var itemsArray: [[String: Any]] = []
        for (index, item) in items.enumerated() {
            itemsArray.append([
                "name": item.name,
                "media_type": classifyKind(fileName: item.name, mimeType: "").rawValue == "video" ? "video" : "image",
                "path": archiveNames[index]
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
        
        // Save to Documents, optionally inside a user-entered relative folder.
        var outputDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let folder = outputFolder.trimmingCharacters(in: .whitespacesAndNewlines)
        if !folder.isEmpty {
            for component in folder.split(separator: "/").map(String.init) {
                let safeComponent = sanitizeFileName(component)
                guard !safeComponent.isEmpty, safeComponent != ".", safeComponent != ".." else { continue }
                outputDir.appendPathComponent(safeComponent, isDirectory: true)
            }
            try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
        }
        let outputURL = outputDir.appendingPathComponent(packageName + suffix)
        try fileData.write(to: outputURL)
        
        return outputURL
    }
    
    // MARK: - Helpers
    private static func unzipData(_ data: Data, to directory: URL) throws -> [MediaItem] {
        var items: [MediaItem] = []
        let archive = try Archive(data: data, accessMode: .read)
        var usedNames = Set<String>()

        for entry in archive {
            guard entry.type == .file,
                  let safeName = safeEntryFileName(entry.path) else { continue }

            let extractedName = uniqueArchiveName(safeName, usedNames: &usedNames, index: usedNames.count + 1)
            usedNames.insert(extractedName)
            let fileURL = directory.appendingPathComponent(extractedName)
            _ = try archive.extract(entry, to: fileURL)
            items.append(contentsOf: mediaItems(for: [fileURL]))
        }
        
        return items
    }

    private static func untarData(_ data: Data, to directory: URL) throws -> [MediaItem] {
        let bytes = [UInt8](data)
        var offset = 0
        var items: [MediaItem] = []
        var usedNames = Set<String>()

        while offset + 512 <= bytes.count {
            if bytes[offset..<offset + 512].allSatisfy({ $0 == 0 }) {
                break
            }
            let entryName = tarString(bytes, offset: offset, length: 100)
            let size = tarSize(bytes, offset: offset + 124)
            let type = bytes[offset + 156]
            offset += 512
            guard size >= 0, offset + size <= bytes.count else {
                throw CryptoUtil.CryptoError.invalidPackage
            }
            if (type == 0 || type == 48), size > 0, let safeName = safeEntryFileName(entryName) {
                let extractedName = uniqueArchiveName(safeName, usedNames: &usedNames, index: usedNames.count + 1)
                usedNames.insert(extractedName)
                let fileURL = directory.appendingPathComponent(extractedName)
                try Data(bytes[offset..<offset + size]).write(to: fileURL)
                items.append(contentsOf: mediaItems(for: [fileURL]))
            }
            offset += ((size + 511) / 512) * 512
        }
        return items
    }

    private static func mediaItems(for urls: [URL]) -> [MediaItem] {
        urls.compactMap { fileURL in
            let name = fileURL.lastPathComponent
            let kind = classifyKind(fileName: name, mimeType: "")
            guard kind == .video || kind == .image else { return nil }
            return MediaItem(
                uri: fileURL.absoluteString,
                name: name,
                mimeType: UTType(filenameExtension: fileNameExtension(name))?.preferredMIMEType ?? "",
                kind: kind,
                source: "媒体包"
            )
        }
    }

    private static func tarString(_ bytes: [UInt8], offset: Int, length: Int) -> String {
        let end = min(offset + length, bytes.count)
        var cursor = offset
        while cursor < end, bytes[cursor] != 0 {
            cursor += 1
        }
        return String(data: Data(bytes[offset..<cursor]), encoding: .utf8) ?? ""
    }

    private static func tarSize(_ bytes: [UInt8], offset: Int) -> Int {
        let end = min(offset + 12, bytes.count)
        var size = 0
        for index in offset..<end {
            let value = bytes[index]
            if value == 0 || value == 32 {
                continue
            }
            if value < 48 || value > 55 {
                break
            }
            size = (size * 8) + Int(value - 48)
        }
        return size
    }
    
    private static func createZipData(entries: [(name: String, data: Data)]) throws -> Data {
        let archive = try Archive(accessMode: .create)

        for entry in entries {
            try archive.addEntry(
                with: entry.name,
                type: .file,
                uncompressedSize: Int64(entry.data.count),
                compressionMethod: .deflate
            ) { position, size in
                let start = Int(position)
                let end = min(start + size, entry.data.count)
                return entry.data.subdata(in: start..<end)
            }
        }

        guard let archiveData = archive.data else {
            throw CryptoUtil.CryptoError.invalidPackage
        }
        return archiveData
    }
    
    private static func uniqueArchiveName(_ name: String, usedNames: inout Set<String>, index: Int) -> String {
        if !usedNames.contains(name) { return name }
        let ext = (name as NSString).pathExtension
        let base = (name as NSString).deletingPathExtension
        if ext.isEmpty { return "\(base)-\(index)" }
        return "\(base)-\(index).\(ext)"
    }

    private static func safeEntryFileName(_ path: String) -> String? {
        guard let name = path.split(separator: "/").last.map(String.init),
              !name.isEmpty,
              name != ".",
              name != ".." else {
            return nil
        }
        return name
    }
}
