import SwiftUI

// MARK: - B站风格配色
enum AppTheme {
    // 深色
    static let darkBg        = Color(hex: 0x17181a)
    static let darkSurface   = Color(hex: 0x212224)
    static let darkCard      = Color(hex: 0x212224)
    static let darkStroke    = Color(hex: 0x2d3036)
    static let darkCardStroke = Color(hex: 0x2a2d33)
    static let darkText      = Color.white
    static let darkSubtext   = Color(hex: 0xaeb4bd)
    static let darkHint      = Color(hex: 0x767d88)
    static let darkGhost     = Color(hex: 0x2c2d30)
    static let darkGhostStroke = Color(hex: 0x3a3b3e)
    static let darkInput     = Color(hex: 0x2c2d30)
    static let darkInputStroke = Color(hex: 0x3a3b3e)
    static let darkDivider   = Color(hex: 0x2c2d30)
    static let darkChip      = Color(hex: 0x2c2d30)
    static let darkThumbnail = Color(hex: 0x2c2d30)
    
    // 浅色
    static let lightBg       = Color(hex: 0xfafafa)
    static let lightSurface  = Color.white
    static let lightCard     = Color(hex: 0xf5f5f5)
    static let lightStroke   = Color(hex: 0xe0e0e0)
    static let lightCardStroke = Color(hex: 0xe0e0e0)
    static let lightText     = Color(hex: 0x1a1a1a)
    static let lightSubtext  = Color(hex: 0x666666)
    static let lightHint     = Color(hex: 0x999999)
    static let lightGhost    = Color(hex: 0xeeeeee)
    static let lightGhostStroke = Color(hex: 0xcccccc)
    static let lightInput    = Color.white
    static let lightInputStroke = Color(hex: 0xe0e0e0)
    static let lightDivider  = Color(hex: 0xe0e0e0)
    static let lightChip     = Color(hex: 0xf0f0f0)
    static let lightThumbnail = Color(hex: 0xe8e8e8)
    
    // 通用
    static let accent = Color(hex: 0xfb7299)
    static let chipActive = Color(hex: 0xfb7299)
}

// MARK: - 环境值扩展
extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

// MARK: - Theme-aware 颜色
struct ThemeColors {
    let isDark: Bool
    
    var bg: Color { isDark ? AppTheme.darkBg : AppTheme.lightBg }
    var surface: Color { isDark ? AppTheme.darkSurface : AppTheme.lightSurface }
    var card: Color { isDark ? AppTheme.darkCard : AppTheme.lightCard }
    var stroke: Color { isDark ? AppTheme.darkStroke : AppTheme.lightStroke }
    var cardStroke: Color { isDark ? AppTheme.darkCardStroke : AppTheme.lightCardStroke }
    var text: Color { isDark ? AppTheme.darkText : AppTheme.lightText }
    var subtext: Color { isDark ? AppTheme.darkSubtext : AppTheme.lightSubtext }
    var hint: Color { isDark ? AppTheme.darkHint : AppTheme.lightHint }
    var ghost: Color { isDark ? AppTheme.darkGhost : AppTheme.lightGhost }
    var ghostStroke: Color { isDark ? AppTheme.darkGhostStroke : AppTheme.lightGhostStroke }
    var input: Color { isDark ? AppTheme.darkInput : AppTheme.lightInput }
    var inputStroke: Color { isDark ? AppTheme.darkInputStroke : AppTheme.lightInputStroke }
    var divider: Color { isDark ? AppTheme.darkDivider : AppTheme.lightDivider }
    var chip: Color { isDark ? AppTheme.darkChip : AppTheme.lightChip }
    var thumbnail: Color { isDark ? AppTheme.darkThumbnail : AppTheme.lightThumbnail }
    var accent: Color { AppTheme.accent }
    var chipActive: Color { AppTheme.chipActive }
}
