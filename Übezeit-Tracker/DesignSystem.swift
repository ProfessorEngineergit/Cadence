import SwiftUI
import Combine

// MARK: - Theme Manager (Dark Mode Toggle)
class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    @Published var isDarkMode: Bool {
        didSet {
            UserDefaults.standard.set(isDarkMode, forKey: "isDarkMode")
            applyTheme()
        }
    }

    private init() {
        self.isDarkMode = UserDefaults.standard.bool(forKey: "isDarkMode")
        // Default to dark mode if not set
        if !UserDefaults.standard.contains(key: "isDarkMode") {
            self.isDarkMode = true
        }
        applyTheme()
    }

    func applyTheme() {
        // Update UI on main thread
        DispatchQueue.main.async {
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                for window in windowScene.windows {
                    window.overrideUserInterfaceStyle = self.isDarkMode ? .dark : .light
                }
            }
        }
    }

    func toggle() {
        isDarkMode.toggle()
        HapticEngine.shared.medium()
    }
}

// UserDefaults extension to check if key exists
extension UserDefaults {
    func contains(key: String) -> Bool {
        return object(forKey: key) != nil
    }
}

// MARK: - Font Setup Instructions
/*
 Exo2 Variable Font Setup:

 1. Die Font-Datei "Exo2-VariableFont_wght.ttf" ist im Fonts Ordner
 2. In Info.plist muss stehen:
    <key>UIAppFonts</key>
    <array>
        <string>Fonts/Exo2-VariableFont_wght.ttf</string>
    </array>
 3. Font-Datei muss Target Membership haben (Xcode > File Inspector > Target Membership aktivieren)
 4. Der Font-Name für .custom() ist "Exo 2" (mit Leerzeichen!)
*/

// MARK: - App Colors
struct AppColors {
    // Primary Colors - Duolingo Style
    static let primary = Color(hex: "58CC02") // Duolingo Green
    static let primaryDark = Color(hex: "46A302")
    static let secondary = Color(hex: "1CB0F6") // Duolingo Blue
    static let secondaryDark = Color(hex: "1899D6")

    // Accent Colors
    static let success = Color(hex: "58CC02")
    static let warning = Color(hex: "FFC800")
    static let error = Color(hex: "FF4B4B")
    static let purple = Color(hex: "CE82FF")

    // OLED Dark Mode - True Black mit subtilen Akzenten
    static let oledBlack = Color(hex: "000000")
    static let darkBg = Color(hex: "000000") // True OLED Black
    static let darkCard = Color(hex: "0D0D0D") // Almost black
    static let darkCardHighlight = Color(hex: "1A1A1A") // Subtle lift
    static let darkCardBorder = Color(hex: "2A2A2A") // Subtle border

    // Light Mode
    static let lightBg = Color(hex: "FFFFFF")
    static let lightCard = Color.white

    // Glow Colors for Dark Mode
    static let glowGreen = Color(hex: "58CC02").opacity(0.4)
    static let glowBlue = Color(hex: "1CB0F6").opacity(0.4)
}

// Hex Color Extension
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Typography (Exo2 Variable Font)
// Die Variable Font nutzt den Namen "Exo 2" (mit Leerzeichen!) mit .weight() Modifier
extension View {
    func appFont(size: CGFloat) -> some View {
        self.font(.custom("Exo 2", size: size).weight(.bold))
    }

    func appFontMedium(size: CGFloat) -> some View {
        self.font(.custom("Exo 2", size: size).weight(.semibold))
    }

    func appFontRegular(size: CGFloat) -> some View {
        self.font(.custom("Exo 2", size: size).weight(.regular))
    }
}

// MARK: - Haptic Engine
class HapticEngine {
    static let shared = HapticEngine()

    private init() {}

    func light() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }

    func medium() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
    }

    func heavy() {
        let generator = UIImpactFeedbackGenerator(style: .heavy)
        generator.impactOccurred()
    }

    func soft() {
        let generator = UIImpactFeedbackGenerator(style: .soft)
        generator.impactOccurred()
    }

    func rigid() {
        let generator = UIImpactFeedbackGenerator(style: .rigid)
        generator.impactOccurred()
    }

    func success() {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
    }

    func warning() {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.warning)
    }

    func error() {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.error)
    }

    func selection() {
        let generator = UISelectionFeedbackGenerator()
        generator.selectionChanged()
    }

    /// Multi-tap celebration pattern
    func celebrate() {
        let generator = UIImpactFeedbackGenerator(style: .heavy)
        generator.impactOccurred()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    /// Button press pattern (down)
    func buttonDown() {
        let generator = UIImpactFeedbackGenerator(style: .rigid)
        generator.impactOccurred(intensity: 0.6)
    }

    /// Button release pattern (up)
    func buttonUp() {
        let generator = UIImpactFeedbackGenerator(style: .soft)
        generator.impactOccurred(intensity: 0.4)
    }
}

// MARK: - Backgrounds
struct BackgroundView: View {
    @Environment(\.colorScheme) var colorScheme

    var body: some View {
        Group {
            if colorScheme == .dark {
                // OLED Black with subtle gradient
                ZStack {
                    Color.black.ignoresSafeArea()

                    // Subtle dark gradient overlay
                    LinearGradient(
                        colors: [
                            AppColors.darkBg.opacity(0.3),
                            Color.black,
                            AppColors.darkBg.opacity(0.2)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()

                    // Subtle ambient glow
                    Circle()
                        .fill(AppColors.primary.opacity(0.05))
                        .frame(width: 400, height: 400)
                        .blur(radius: 100)
                        .offset(x: -50, y: -300)

                    Circle()
                        .fill(AppColors.secondary.opacity(0.05))
                        .frame(width: 350, height: 350)
                        .blur(radius: 100)
                        .offset(x: 100, y: 400)
                }
            } else {
                // Light Mode - Clean white with subtle gradients
                ZStack {
                    Color.white.ignoresSafeArea()

                    LinearGradient(
                        colors: [
                            AppColors.secondary.opacity(0.08),
                            AppColors.primary.opacity(0.05),
                            Color.white
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                }
            }
        }
    }
}

// MARK: - 3D Duolingo-Style Card
struct Card3D<Content: View>: View {
    @Environment(\.colorScheme) var colorScheme
    let content: Content
    var shadowColor: Color = AppColors.primary
    var isPressed: Bool = false

    init(shadowColor: Color = AppColors.primary, isPressed: Bool = false, @ViewBuilder content: () -> Content) {
        self.content = content()
        self.shadowColor = shadowColor
        self.isPressed = isPressed
    }

    var body: some View {
        content
            .padding()
            .background(
                ZStack {
                    // Bottom shadow layer (the 3D effect)
                    RoundedRectangle(cornerRadius: 20)
                        .fill(colorScheme == .dark ? shadowColor.opacity(0.4) : shadowColor.opacity(0.3))
                        .offset(y: isPressed ? 2 : 6)

                    // Main card
                    RoundedRectangle(cornerRadius: 20)
                        .fill(colorScheme == .dark ? AppColors.darkCard : AppColors.lightCard)

                    // Top highlight
                    RoundedRectangle(cornerRadius: 20)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(colorScheme == .dark ? 0.08 : 0.6),
                                    Color.clear
                                ],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )
                }
            )
            .offset(y: isPressed ? 4 : 0)
            .animation(.spring(response: 0.2, dampingFraction: 0.6), value: isPressed)
    }
}

// MARK: - Interactive 3D Button
struct Button3D: View {
    let title: String
    let icon: String?
    let color: Color
    let action: () -> Void

    @State private var isPressed = false
    @Environment(\.colorScheme) var colorScheme

    init(_ title: String, icon: String? = nil, color: Color = AppColors.primary, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.color = color
        self.action = action
    }

    var body: some View {
        Button(action: {}) {
            HStack(spacing: 10) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: 20, weight: .bold))
                }
                Text(title)
                    .appFont(size: 18)
            }
            .foregroundStyle(.white)
            .padding(.vertical, 16)
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity)
            .background(
                ZStack {
                    // Bottom shadow (darker shade)
                    RoundedRectangle(cornerRadius: 16)
                        .fill(color.opacity(0.6))
                        .offset(y: isPressed ? 2 : 5)

                    // Main button
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [color, color.opacity(0.85)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                    // Top shine
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.3), Color.clear],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )
                }
            )
            .offset(y: isPressed ? 3 : 0)
        }
        .buttonStyle(PlainButtonStyle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isPressed {
                        isPressed = true
                        HapticEngine.shared.buttonDown()
                    }
                }
                .onEnded { _ in
                    isPressed = false
                    HapticEngine.shared.buttonUp()
                    action()
                }
        )
        .animation(.spring(response: 0.2, dampingFraction: 0.6), value: isPressed)
    }
}

// MARK: - Progress Ring with Animation
struct AnimatedProgressRing: View {
    let progress: Double
    let lineWidth: CGFloat
    let gradient: [Color]

    @State private var animatedProgress: Double = 0
    @State private var rotation: Double = 0

    init(progress: Double, lineWidth: CGFloat = 20, gradient: [Color] = [AppColors.primary, AppColors.secondary]) {
        self.progress = progress
        self.lineWidth = lineWidth
        self.gradient = gradient
    }

    var body: some View {
        ZStack {
            // Background ring
            Circle()
                .stroke(Color.gray.opacity(0.2), lineWidth: lineWidth)

            // Progress ring
            Circle()
                .trim(from: 0, to: CGFloat(animatedProgress))
                .stroke(
                    AngularGradient(
                        colors: gradient + [gradient.first ?? AppColors.primary],
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))

            // Glowing tip
            if animatedProgress > 0 {
                Circle()
                    .fill(gradient.last ?? AppColors.secondary)
                    .frame(width: lineWidth, height: lineWidth)
                    .offset(y: -100) // Adjust based on ring size
                    .rotationEffect(.degrees(360 * animatedProgress - 90))
                    .shadow(color: gradient.last?.opacity(0.6) ?? Color.blue.opacity(0.6), radius: 8)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 1.0, dampingFraction: 0.7)) {
                animatedProgress = progress
            }
        }
        .onChange(of: progress) { _, newValue in
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                animatedProgress = newValue
            }
        }
    }
}

// MARK: - Bouncy Scale Button Modifier
struct BouncyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1.0)
            .animation(.spring(response: 0.2, dampingFraction: 0.5), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, isPressed in
                if isPressed {
                    HapticEngine.shared.light()
                }
            }
    }
}

extension View {
    func bouncyButton() -> some View {
        self.buttonStyle(BouncyButtonStyle())
    }
}

// MARK: - Confetti View
struct ConfettiView: View {
    @State private var particles: [ConfettiParticle] = []
    let colors: [Color] = [AppColors.primary, AppColors.secondary, AppColors.warning, AppColors.purple, AppColors.error]

    struct ConfettiParticle: Identifiable {
        let id = UUID()
        var x: CGFloat
        var y: CGFloat
        var rotation: Double
        var scale: CGFloat
        var color: Color
        var velocity: CGFloat
        var rotationSpeed: Double
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                ForEach(particles) { particle in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(particle.color)
                        .frame(width: 10, height: 6)
                        .scaleEffect(particle.scale)
                        .rotationEffect(.degrees(particle.rotation))
                        .position(x: particle.x, y: particle.y)
                }
            }
            .onAppear {
                generateParticles(in: geometry.size)
                animateParticles(in: geometry.size)
            }
        }
        .allowsHitTesting(false)
    }

    private func generateParticles(in size: CGSize) {
        particles = (0..<50).map { _ in
            ConfettiParticle(
                x: CGFloat.random(in: 0...size.width),
                y: -20,
                rotation: Double.random(in: 0...360),
                scale: CGFloat.random(in: 0.5...1.5),
                color: colors.randomElement() ?? AppColors.primary,
                velocity: CGFloat.random(in: 2...6),
                rotationSpeed: Double.random(in: -10...10)
            )
        }
    }

    private func animateParticles(in size: CGSize) {
        Timer.scheduledTimer(withTimeInterval: 0.016, repeats: true) { timer in
            for i in particles.indices {
                particles[i].y += particles[i].velocity
                particles[i].rotation += particles[i].rotationSpeed
                particles[i].velocity += 0.1 // gravity
            }

            // Remove particles that are off screen
            particles.removeAll { $0.y > size.height + 50 }

            if particles.isEmpty {
                timer.invalidate()
            }
        }
    }
}

// MARK: - Shimmer Effect
struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geometry in
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0),
                            Color.white.opacity(0.3),
                            Color.white.opacity(0)
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: geometry.size.width * 0.5)
                    .offset(x: -geometry.size.width * 0.25 + phase * geometry.size.width * 1.5)
                    .mask(content)
                }
            )
            .onAppear {
                withAnimation(.linear(duration: 2).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}

extension View {
    func shimmer() -> some View {
        self.modifier(ShimmerModifier())
    }
}

// MARK: - Pulse Animation Modifier
struct PulseModifier: ViewModifier {
    @State private var isPulsing = false
    let intensity: CGFloat

    init(intensity: CGFloat = 1.1) {
        self.intensity = intensity
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(isPulsing ? intensity : 1.0)
            .animation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true), value: isPulsing)
            .onAppear {
                isPulsing = true
            }
    }
}

extension View {
    func pulse(intensity: CGFloat = 1.1) -> some View {
        self.modifier(PulseModifier(intensity: intensity))
    }
}

// MARK: - Glow Effect
extension View {
    func glow(color: Color, radius: CGFloat = 10) -> some View {
        self
            .shadow(color: color.opacity(0.5), radius: radius / 2)
            .shadow(color: color.opacity(0.3), radius: radius)
    }
}

// MARK: - Legacy Glass Card (for compatibility)
struct GlassCard<Content: View>: View {
    @Environment(\.colorScheme) var colorScheme
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        Card3D(shadowColor: colorScheme == .dark ? AppColors.darkCardHighlight : Color.gray.opacity(0.3)) {
            content
        }
    }
}

struct GlassModifier: ViewModifier {
    @Environment(\.colorScheme) var colorScheme

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: 15)
                    .fill(colorScheme == .dark ? AppColors.darkCard : AppColors.lightCard)
                    .shadow(color: Color.black.opacity(0.2), radius: 5, x: 0, y: 3)
            )
    }
}

extension View {
    func glassBackground() -> some View {
        self.modifier(GlassModifier())
    }
}

// MARK: - Preview
struct DesignPreview: View {
    @State private var showConfetti = false

    var body: some View {
        ZStack {
            BackgroundView()

            ScrollView {
                VStack(spacing: 20) {
                    Text("Übezeit Tracker")
                        .appFont(size: 32)
                        .foregroundStyle(.primary)

                    Card3D(shadowColor: AppColors.primary) {
                        HStack {
                            Text("3D Card Example")
                                .appFont(size: 18)
                            Spacer()
                            Image(systemName: "star.fill")
                                .foregroundStyle(AppColors.warning)
                        }
                    }
                    .padding(.horizontal)

                    AnimatedProgressRing(progress: 0.75)
                        .frame(width: 150, height: 150)

                    Button3D("Üben starten", icon: "play.fill", color: AppColors.primary) {
                        showConfetti = true
                    }
                    .padding(.horizontal)

                    Button3D("Stoppen", icon: "stop.fill", color: AppColors.error) {}
                        .padding(.horizontal)
                }
                .padding()
            }

            if showConfetti {
                ConfettiView()
                    .onAppear {
                        HapticEngine.shared.celebrate()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                            showConfetti = false
                        }
                    }
            }
        }
    }
}

#Preview {
    DesignPreview()
}

#Preview("Dark Mode") {
    DesignPreview()
        .preferredColorScheme(.dark)
}
