import Foundation
import CryptoKit
import CommonCrypto

// MARK: - AES-256-GCM + PBKDF2 加密/解密（与 Android 端兼容）
enum CryptoUtil {
    static let magic = "MAOQIU_PLAYER_ENC_V1"
    static let format = "MaoqiuPlayerMediaPackage"
    static let defaultPhrase = "MaoqiuPlayer local media package v1"
    static let defaultIterations = 390000
    
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
        let sealedBox = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce), ciphertext: payload.dropLast(16), tag: payload.suffix(16))
        
        // Use raw AES-GCM with AAD via CommonCrypto for AAD support
        return try decryptAESGCM(key: key, nonce: nonce, ciphertext: payload.dropLast(16), tag: payload.suffix(16), aad: aad)
    }
    
    // MARK: - Encrypt (加密为 .mqp payload)
    static func encryptPayload(_ data: Data, salt: Data, nonce: Data, iterations: Int = defaultIterations) throws -> Data {
        guard let key = pbkdf2(password: defaultPhrase, salt: salt, iterations: iterations) else {
            throw CryptoError.keyDerivationFailed
        }
        
        let aad = Data(magic.utf8)
        return try encryptAESGCM(key: key, nonce: nonce, plaintext: data, aad: aad)
    }
    
    // MARK: - Low-level AES-GCM with AAD via CommonCrypto
    private static func encryptAESGCM(key: Data, nonce: Data, plaintext: Data, aad: Data) throws -> Data {
        let keyBytes = [UInt8](key)
        let nonceBytes = [UInt8](nonce)
        let plainBytes = [UInt8](plaintext)
        let aadBytes = [UInt8](aad)
        
        var tagLength = 16
        var ciphertext = [UInt8](repeating: 0, count: plainBytes.count + tagLength)
        var ciphertextLength = 0
        
        // Create context
        let status = keyBytes.withUnsafeBufferPointer { keyPtr in
            nonceBytes.withUnsafeBufferPointer { noncePtr in
                plainBytes.withUnsafeBufferPointer { plainPtr in
                    aadBytes.withUnsafeBufferPointer { aadPtr in
                        ciphertext.withUnsafeMutableBufferPointer { cipherPtr in
                            var cryptorRef: CCCryptorRef?
                            
                            var createStatus = CCCryptorCreateWithMode(
                                CCOperation(kCCEncrypt),
                                CCMode(kCCModeGCM),
                                CCAlgorithm(kCCAlgorithmAES),
                                CCPadding(ccNoPadding),
                                nil,
                                keyPtr.baseAddress, keyBytes.count,
                                nil, 0,
                                0, 0,
                                &cryptorRef
                            )
                            guard createStatus == kCCSuccess, let cryptor = cryptorRef else { return createStatus }
                            
                            // Set IV
                            createStatus = CCCryptorAddParameter(cryptor, kCCParameterIV, noncePtr.baseAddress, nonceBytes.count)
                            guard createStatus == kCCSuccess else { return createStatus }
                            
                            // Add AAD
                            if !aadBytes.isEmpty {
                                createStatus = CCCryptorAddParameter(cryptor, kCCParameterAuthData, aadPtr.baseAddress, aadBytes.count)
                                guard createStatus == kCCSuccess else { return createStatus }
                            }
                            
                            // Encrypt
                            var moved = 0
                            createStatus = CCCryptorUpdate(cryptor, plainPtr.baseAddress, plainBytes.count, cipherPtr.baseAddress, cipherBytes.count, &moved)
                            ciphertextLength = moved
                            guard createStatus == kCCSuccess else { return createStatus }
                            
                            // Final
                            var finalMoved = 0
                            createStatus = CCCryptorFinal(cryptor, cipherPtr.baseAddress! + moved, cipherBytes.count - moved, &finalMoved)
                            ciphertextLength += finalMoved
                            guard createStatus == kCCSuccess else { return createStatus }
                            
                            // Get tag
                            var tag = [UInt8](repeating: 0, count: 16)
                            createStatus = tag.withUnsafeMutableBufferPointer { tagPtr in
                                CCCryptorGetParameter(cryptor, kCCParameterAuthTag, tagPtr.baseAddress!, &tagLength)
                            }
                            guard createStatus == kCCSuccess else { return createStatus }
                            
                            CCCryptorRelease(cryptor)
                            
                            // Append tag to ciphertext
                            for i in 0..<16 {
                                cipherPtr[ciphertextLength + i] = tag[i]
                            }
                            ciphertextLength += 16
                            
                            return kCCSuccess
                        }
                    }
                }
            }
        }
        
        guard status == kCCSuccess else { throw CryptoError.encryptionFailed }
        return Data(ciphertext.prefix(ciphertextLength))
    }
    
    private static func decryptAESGCM(key: Data, nonce: Data, ciphertext: Data, tag: Data, aad: Data) throws -> Data {
        let keyBytes = [UInt8](key)
        let nonceBytes = [UInt8](nonce)
        let cipherBytes = [UInt8](ciphertext)
        let tagBytes = [UInt8](tag)
        let aadBytes = [UInt8](aad)
        
        var plaintext = [UInt8](repeating: 0, count: cipherBytes.count + 16)
        var plaintextLength = 0
        
        let status = keyBytes.withUnsafeBufferPointer { keyPtr in
            nonceBytes.withUnsafeBufferPointer { noncePtr in
                cipherBytes.withUnsafeBufferPointer { cipherPtr in
                    aadBytes.withUnsafeBufferPointer { aadPtr in
                        tagBytes.withUnsafeBufferPointer { tagPtr in
                            plaintext.withUnsafeMutableBufferPointer { plainPtr in
                                var cryptorRef: CCCryptorRef?
                                
                                var createStatus = CCCryptorCreateWithMode(
                                    CCOperation(kCCDecrypt),
                                    CCMode(kCCModeGCM),
                                    CCAlgorithm(kCCAlgorithmAES),
                                    CCPadding(ccNoPadding),
                                    nil,
                                    keyPtr.baseAddress, keyBytes.count,
                                    nil, 0,
                                    0, 0,
                                    &cryptorRef
                                )
                                guard createStatus == kCCSuccess, let cryptor = cryptorRef else { return createStatus }
                                
                                // Set IV
                                createStatus = CCCryptorAddParameter(cryptor, kCCParameterIV, noncePtr.baseAddress, nonceBytes.count)
                                guard createStatus == kCCSuccess else { return createStatus }
                                
                                // Add AAD
                                if !aadBytes.isEmpty {
                                    createStatus = CCCryptorAddParameter(cryptor, kCCParameterAuthData, aadPtr.baseAddress, aadBytes.count)
                                    guard createStatus == kCCSuccess else { return createStatus }
                                }
                                
                                // Set tag
                                createStatus = CCCryptorAddParameter(cryptor, kCCParameterAuthTag, tagPtr.baseAddress, tagBytes.count)
                                guard createStatus == kCCSuccess else { return createStatus }
                                
                                // Decrypt
                                var moved = 0
                                createStatus = CCCryptorUpdate(cryptor, cipherPtr.baseAddress, cipherBytes.count, plainPtr.baseAddress, plaintext.count, &moved)
                                plaintextLength = moved
                                guard createStatus == kCCSuccess else { return createStatus }
                                
                                // Final
                                var finalMoved = 0
                                createStatus = CCCryptorFinal(cryptor, plainPtr.baseAddress! + moved, plaintext.count - moved, &finalMoved)
                                plaintextLength += finalMoved
                                
                                CCCryptorRelease(cryptor)
                                
                                return createStatus
                            }
                        }
                    }
                }
            }
        }
        
        guard status == kCCSuccess else { throw CryptoError.decryptionFailed }
        return Data(plaintext.prefix(plaintextLength))
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
        
        var errorDescription: String? {
            switch self {
            case .keyDerivationFailed: return "密钥派生失败"
            case .encryptionFailed: return "加密失败"
            case .decryptionFailed: return "解密失败"
            case .invalidPackage: return "无效的媒体包"
            case .checksumFailed: return "文件校验失败"
            }
        }
    }
}
