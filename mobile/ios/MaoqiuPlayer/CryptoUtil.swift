import Foundation
import CryptoKit
import CommonCrypto

// MARK: - AES-256-GCM + PBKDF2 加密/解密（与 Android 端兼容）
enum CryptoUtil {
    static let magic = "MAOQIU_PLAYER_ENC_V1"
    static let format = "MaoqiuPlayerMediaPackage"
    static let defaultPhrase = "MaoqiuPlayer local media package v1"
    static let defaultIterations = 390000
    static let liteAppKey = SHA256.hash(data: Data("Maoqiu Secure Lite v1 built-in application key".utf8)).withUnsafeBytes { Data($0) }
    
    // MARK: - PBKDF2 Key Derivation (CommonCrypto, compatible with Java PBKDF2WithHmacSHA256)
    static func pbkdf2(password: String, salt: Data, iterations: Int = defaultIterations, keyLength: Int = 32) -> Data? {
        let passwordData = Array(password.utf8)
        let saltBytes = [UInt8](salt)
        var derivedKey = [UInt8](repeating: 0, count: keyLength)
        
        let status = CCKeyDerivationPBKDF(
            CCPBKDFAlgorithm(kCCPBKDF2),
            password, passwordData.count,
            saltBytes, saltBytes.count,
            CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
            UInt32(iterations),
            &derivedKey, derivedKey.count
        )
        
        guard status == kCCSuccess else { return nil }
        return Data(derivedKey)
    }
    
    // MARK: - Decrypt (解密 .mqp payload)
    static func decryptPayload(_ payload: Data, salt: Data, nonce: Data, iterations: Int = defaultIterations) throws -> Data {
        guard let key = pbkdf2(password: defaultPhrase, salt: salt, iterations: iterations) else {
            throw CryptoError.keyDerivationFailed
        }
        
        let symmetricKey = SymmetricKey(data: key)
        let aad = Data(magic.utf8)
        let sealedBox = try AES.GCM.SealedBox(
            nonce: AES.GCM.Nonce(data: nonce),
            ciphertext: Data(payload.dropLast(16)),
            tag: Data(payload.suffix(16))
        )
        return try AES.GCM.open(sealedBox, using: symmetricKey, authenticating: aad)
    }
    
    // MARK: - Encrypt (加密为 .mqp payload)
    static func encryptPayload(_ data: Data, salt: Data, nonce: Data, iterations: Int = defaultIterations) throws -> Data {
        guard let key = pbkdf2(password: defaultPhrase, salt: salt, iterations: iterations) else {
            throw CryptoError.keyDerivationFailed
        }
        
        let symmetricKey = SymmetricKey(data: key)
        let aad = Data(magic.utf8)
        let sealedBox = try AES.GCM.seal(data, using: symmetricKey, nonce: AES.GCM.Nonce(data: nonce), authenticating: aad)
        var output = sealedBox.ciphertext
        output.append(sealedBox.tag)
        return output
    }

    static func decryptRawAESGCM(_ payload: Data, key: Data, nonce: Data) throws -> Data {
        let sealedBox = try AES.GCM.SealedBox(
            nonce: AES.GCM.Nonce(data: nonce),
            ciphertext: Data(payload.dropLast(16)),
            tag: Data(payload.suffix(16))
        )
        return try AES.GCM.open(sealedBox, using: SymmetricKey(data: key))
    }
    
    // MARK: - SHA-256
    static func sha256(_ data: Data) -> String {
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
    
    // MARK: - Hex helpers
    static func hexToBytes(_ hex: String) -> Data {
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let nextIndex = hex.index(index, offsetBy: 2)
            let byteString = String(hex[index..<nextIndex])
            if let byte = UInt8(byteString, radix: 16) {
                data.append(byte)
            }
            index = nextIndex
        }
        return data
    }
    
    static func bytesToHex(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }
    
    enum CryptoError: Error, LocalizedError {
        case keyDerivationFailed
        case encryptionFailed
        case decryptionFailed
        case invalidPackage
        case checksumFailed
        case unsupportedSecurePackage
        
        var errorDescription: String? {
            switch self {
            case .keyDerivationFailed: return "密钥派生失败"
            case .encryptionFailed: return "加密失败"
            case .decryptionFailed: return "解密失败"
            case .invalidPackage: return "无效的媒体包"
            case .checksumFailed: return "文件校验失败"
            case .unsupportedSecurePackage: return "该加密文件需要用户名和密码，请使用桌面版打开"
            }
        }
    }
}
