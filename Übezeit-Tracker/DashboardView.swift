import SwiftUI

struct DashboardView: View {
    @ObservedObject var manager: PracticeManager
    @ObservedObject var themeManager = ThemeManager.shared
    @State private var showingGoalSheet = false
    @State private var showingSuccess = false
    @State private var showingLogPractice = false
    @State private var showConfetti = false

    // Animations
    @State private var pulse = false
    @State private var ringAppeared = false

    var body: some View {
        ZStack {
            BackgroundView()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // Header with Menu
                    headerView

                    // Motivational Message
                    motivationalCard

                    // Progress Circle
                    progressCircleView

                    // Action Buttons Row (Timer + Nachtragen)
                    actionButtonsRow

                    // Today's Goal Info
                    todayInfoCard

                    // Übe-Saldo (practice debt/surplus)
                    saldoCard

                    // Weekly Progress Bar Chart
                    weeklyProgressCard

                    // Daily Bar Chart with integrated history
                    dailyBarChartCard

                    // Stats Row
                    statsRow

                    Spacer(minLength: 60)
                }
            }

            // Confetti Overlay
            if showConfetti {
                ConfettiView()
                    .allowsHitTesting(false)
            }

            // Success Overlay
            if showingSuccess {
                SuccessView(isShowing: $showingSuccess, showConfetti: $showConfetti)
            }
        }
        .sheet(isPresented: $showingGoalSheet) {
            GoalSettingView(manager: manager)
        }
        .sheet(isPresented: $showingLogPractice) {
            LogPracticeView(manager: manager)
        }
        .onAppear {
            checkMondayGoal()
            manager.calculateLifetimeMinutes()
        }
    }

    // MARK: - Subviews

    private var headerView: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(currentDateString())
                    .appFontRegular(size: 14)
                    .opacity(0.6)
                Text(manager.isTimerRunning ? "Übt gerade..." : "Übersicht")
                    .appFont(size: 32)
            }

            Spacer()

            // Menu Button
            Menu {
                Button(action: {
                    HapticEngine.shared.selection()
                    showingGoalSheet = true
                }) {
                    Label("Ziele anpassen", systemImage: "target")
                }

                Button(action: {
                    HapticEngine.shared.selection()
                    showingLogPractice = true
                }) {
                    Label("Übezeit nachtragen", systemImage: "clock.badge.checkmark")
                }

                Divider()

                Button(action: {
                    themeManager.toggle()
                }) {
                    Label(themeManager.isDarkMode ? "Light Mode" : "Dark Mode",
                          systemImage: themeManager.isDarkMode ? "sun.max.fill" : "moon.fill")
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(AppColors.darkCard.opacity(0.5))
                        .frame(width: 44, height: 44)

                    Image(systemName: "ellipsis")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.primary)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 60)
    }

    private var motivationalCard: some View {
        Card3D(shadowColor: AppColors.secondary.opacity(0.5)) {
            HStack {
                Text(manager.getMotivationalMessage())
                    .appFontMedium(size: 16)
                    .foregroundStyle(.primary)
                Spacer()
            }
        }
        .padding(.horizontal, 20)
    }

    private var progressCircleView: some View {
        ZStack {
            // Background glow
            Circle()
                .fill(
                    RadialGradient(
                        colors: [AppColors.primary.opacity(0.2), Color.clear],
                        center: .center,
                        startRadius: 80,
                        endRadius: 150
                    )
                )
                .frame(width: 280, height: 280)
                .blur(radius: 20)

            AnimatedProgressRing(
                progress: manager.progressForToday(),
                lineWidth: 22,
                gradient: [AppColors.primary, AppColors.secondary]
            )
            .frame(width: 200, height: 200)

            VStack(spacing: 4) {
                if manager.isTimerRunning {
                    Text(timeString(from: manager.currentElapsed))
                        .appFont(size: 44)
                        .contentTransition(.numericText())
                        .foregroundStyle(.primary)
                } else {
                    if manager.todayGoalMinutes() > 0 {
                        Text("\(Int(manager.progressForToday() * 100))%")
                            .appFont(size: 48)
                            .foregroundStyle(.primary)
                        Text("vom Tagesziel")
                            .appFontRegular(size: 14)
                            .opacity(0.6)
                    } else {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 50))
                            .foregroundStyle(AppColors.success)
                            .symbolEffect(.bounce, value: ringAppeared)
                        Text("Heute frei!")
                            .appFont(size: 20)
                            .opacity(0.8)
                    }
                }
            }
        }
        .padding(.vertical, 10)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                ringAppeared = true
            }
        }
    }

    private var actionButtonsRow: some View {
        VStack(spacing: 12) {
            // Main Timer Button
            Button3D(
                manager.isTimerRunning ? "Stoppen" : "Üben starten",
                icon: manager.isTimerRunning ? "stop.fill" : "play.fill",
                color: manager.isTimerRunning ? AppColors.error : AppColors.primary
            ) {
                handleTimerAction()
            }
            .scaleEffect(pulse ? 1.03 : 1.0)
            .onAppear {
                if manager.isTimerRunning {
                    withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                        pulse = true
                    }
                }
            }
            .onChange(of: manager.isTimerRunning) { _, isRunning in
                if isRunning {
                    withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                        pulse = true
                    }
                } else {
                    withAnimation { pulse = false }
                }
            }

            // Nachtragen Button
            Button(action: {
                HapticEngine.shared.selection()
                showingLogPractice = true
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "clock.badge.checkmark")
                        .font(.system(size: 16, weight: .semibold))
                    Text("Übezeit nachtragen")
                        .appFontMedium(size: 15)
                }
                .foregroundStyle(AppColors.secondary)
                .padding(.vertical, 12)
                .padding(.horizontal, 20)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(AppColors.secondary.opacity(0.15))
                )
            }
        }
        .padding(.horizontal, 20)
    }

    private var todayInfoCard: some View {
        Card3D(shadowColor: manager.practicedTodayMinutes() >= manager.todayGoalMinutes() && manager.todayGoalMinutes() > 0 ? AppColors.success : AppColors.secondary.opacity(0.5)) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Heute")
                        .appFontRegular(size: 14)
                        .opacity(0.6)

                    if manager.todayGoalMinutes() == 0 {
                        Text("Heute musst du nicht üben")
                            .appFont(size: 18)
                            .foregroundStyle(AppColors.success)
                    } else {
                        HStack(spacing: 4) {
                            Text("\(manager.practicedTodayMinutes())")
                                .appFont(size: 28)
                                .foregroundStyle(AppColors.primary)
                            Text("/ \(manager.todayGoalMinutes()) min")
                                .appFontMedium(size: 18)
                                .opacity(0.7)
                        }
                    }
                }
                Spacer()

                if manager.todayGoalMinutes() > 0 && manager.practicedTodayMinutes() >= manager.todayGoalMinutes() {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(AppColors.success)
                        .symbolEffect(.bounce, value: manager.practicedTodayMinutes())
                }
            }
        }
        .padding(.horizontal, 20)
    }

    private var saldoCard: some View {
        let saldo = manager.calculatePracticeSaldo()
        let isPositive = saldo >= 0

        return Card3D(shadowColor: isPositive ? AppColors.success.opacity(0.5) : AppColors.error.opacity(0.5)) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Übe-Saldo")
                        .appFontRegular(size: 14)
                        .opacity(0.6)

                    HStack(spacing: 8) {
                        Image(systemName: isPositive ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(isPositive ? AppColors.success : AppColors.error)

                        Text(isPositive ? "+\(saldo) min" : "\(saldo) min")
                            .appFont(size: 24)
                            .foregroundStyle(isPositive ? AppColors.success : AppColors.error)
                    }
                }
                Spacer()

                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(isPositive ? AppColors.success.opacity(0.15) : AppColors.error.opacity(0.15))
                        .frame(width: 90, height: 36)

                    Text(isPositive ? (saldo > 0 ? "Guthaben" : "Ausgeglichen") : "Schuld")
                        .appFontMedium(size: 13)
                        .foregroundStyle(isPositive ? AppColors.success : AppColors.error)
                }
            }
        }
        .padding(.horizontal, 20)
    }

    private var weeklyProgressCard: some View {
        Card3D(shadowColor: AppColors.secondary.opacity(0.4)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Wochenziel")
                        .appFont(size: 16)
                    Spacer()
                    Text("\(manager.totalPracticedThisWeekMinutes())")
                        .appFont(size: 20)
                        .foregroundStyle(AppColors.primary)
                    Text("/ \(PracticeManager.weeklyGoalMinutes) min")
                        .appFontMedium(size: 16)
                        .opacity(0.6)
                }

                // Progress Bar
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 20)

                        RoundedRectangle(cornerRadius: 10)
                            .fill(
                                LinearGradient(
                                    colors: [AppColors.primary, AppColors.secondary],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: geometry.size.width * CGFloat(manager.progressForWeek()), height: 20)
                            .animation(.spring(response: 0.8, dampingFraction: 0.7), value: manager.progressForWeek())

                        // Shine effect
                        if manager.progressForWeek() > 0 {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(
                                    LinearGradient(
                                        colors: [Color.white.opacity(0.4), Color.clear],
                                        startPoint: .top,
                                        endPoint: .center
                                    )
                                )
                                .frame(width: geometry.size.width * CGFloat(manager.progressForWeek()), height: 10)
                        }
                    }
                }
                .frame(height: 20)
            }
        }
        .padding(.horizontal, 20)
    }

    private var dailyBarChartCard: some View {
        Card3D(shadowColor: AppColors.purple.opacity(0.3)) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Diese Woche")
                    .appFont(size: 16)

                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(Weekday.orderedCases, id: \.self) { day in
                        DayBarView(
                            day: day,
                            practiced: manager.practicedMinutes(for: day),
                            goal: manager.goals[day] ?? 0,
                            isToday: day == manager.currentWeekday()
                        )
                    }
                }
                .frame(height: 120)
            }
        }
        .padding(.horizontal, 20)
    }

    private var statsRow: some View {
        Card3D(shadowColor: AppColors.secondary.opacity(0.3)) {
            HStack {
                Image(systemName: "clock.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(AppColors.secondary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Gesamt geübt")
                        .appFontRegular(size: 14)
                        .opacity(0.6)
                    Text("\(manager.totalLifetimeMinutes) Minuten")
                        .appFont(size: 22)
                        .foregroundStyle(AppColors.secondary)
                }

                Spacer()

                // Format as hours if > 60 min
                if manager.totalLifetimeMinutes >= 60 {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("= \(manager.totalLifetimeMinutes / 60)h \(manager.totalLifetimeMinutes % 60)m")
                            .appFontMedium(size: 14)
                            .opacity(0.7)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
    }

    // MARK: - Logic
    func checkMondayGoal() {
        guard manager.isMonday() else { return }

        let lastSetKey = "lastGoalSetDate"
        let lastSet = UserDefaults.standard.object(forKey: lastSetKey) as? Date

        if lastSet == nil || !Calendar.current.isDateInToday(lastSet!) {
            showingGoalSheet = true
        }
    }

    func handleTimerAction() {
        if manager.isTimerRunning {
            manager.toggleTimer()
            manager.calculateLifetimeMinutes()

            withAnimation(.spring()) {
                showingSuccess = true
            }
        } else {
            manager.toggleTimer()
        }
    }

    func currentDateString() -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "de_DE")
        return formatter.string(from: Date())
    }

    func timeString(from timeInterval: TimeInterval) -> String {
        let minutes = Int(timeInterval) / 60
        let seconds = Int(timeInterval) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

// MARK: - Stat Card
struct StatCard: View {
    let icon: String
    let value: String
    let label: String
    let color: Color

    var body: some View {
        Card3D(shadowColor: color.opacity(0.3)) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundStyle(color)

                Text(value)
                    .appFont(size: 24)

                Text(label)
                    .appFontRegular(size: 12)
                    .opacity(0.6)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Day Bar View for Chart
struct DayBarView: View {
    let day: Weekday
    let practiced: Int
    let goal: Int
    let isToday: Bool

    @State private var isPulsing = false
    @State private var appeared = false

    private var maxHeight: CGFloat { 70 }

    private var barHeight: CGFloat {
        guard goal > 0 else {
            return practiced > 0 ? min(CGFloat(practiced) / 30.0 * maxHeight, maxHeight) : 8
        }
        let progress = min(CGFloat(practiced) / CGFloat(goal), 1.5)
        return max(progress * maxHeight, 8)
    }

    private var barColor: Color {
        if goal == 0 && practiced == 0 {
            return Color.gray.opacity(0.3)
        }
        if goal > 0 && practiced >= goal {
            return AppColors.success
        }
        if practiced > 0 {
            return AppColors.primary
        }
        return goal > 0 ? AppColors.primary.opacity(0.3) : Color.gray.opacity(0.3)
    }

    var body: some View {
        VStack(spacing: 6) {
            // Show practiced minutes above bar if practiced
            if practiced > 0 {
                Text("\(practiced)")
                    .appFont(size: 12)
                    .foregroundStyle(barColor)
                    .scaleEffect(isPulsing ? 1.15 : 1.0)
            } else {
                Text(" ")
                    .appFont(size: 12)
            }

            // Bar
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.gray.opacity(0.15))
                    .frame(height: maxHeight)

                RoundedRectangle(cornerRadius: 6)
                    .fill(
                        LinearGradient(
                            colors: [barColor, barColor.opacity(0.7)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(height: appeared ? barHeight : 0)
                    .animation(.spring(response: 0.8, dampingFraction: 0.6).delay(Double(Weekday.orderedCases.firstIndex(of: day) ?? 0) * 0.08), value: appeared)

                // Shine
                if practiced > 0 {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.3), Color.clear],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )
                        .frame(height: appeared ? barHeight : 0)
                }
            }
            .scaleEffect(practiced > 0 && isPulsing ? 1.03 : 1.0)

            // Day label
            ZStack {
                if isToday {
                    Circle()
                        .fill(AppColors.primary)
                        .frame(width: 28, height: 28)
                }

                Text(day.shortName)
                    .appFont(size: 11)
                    .foregroundStyle(isToday ? .white : .primary)
                    .opacity(isToday ? 1.0 : 0.6)
            }
        }
        .frame(maxWidth: .infinity)
        .onAppear {
            appeared = true
            if practiced > 0 {
                withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true).delay(0.5)) {
                    isPulsing = true
                }
            }
        }
    }
}

// MARK: - Success View
struct SuccessView: View {
    @Binding var isShowing: Bool
    @Binding var showConfetti: Bool
    @State private var scale: CGFloat = 0.5
    @State private var opacity: Double = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea()
                .onTapGesture {
                    dismissView()
                }

            VStack(spacing: 24) {
                ZStack {
                    Circle()
                        .fill(AppColors.success.opacity(0.2))
                        .frame(width: 120, height: 120)

                    Image(systemName: "checkmark.circle.fill")
                        .resizable()
                        .frame(width: 80, height: 80)
                        .foregroundStyle(AppColors.success)
                        .symbolEffect(.bounce, value: isShowing)
                }

                Text("Gut gemacht!")
                    .appFont(size: 32)
                    .foregroundStyle(.white)

                Text("Übungssession gespeichert")
                    .appFontRegular(size: 16)
                    .foregroundStyle(.white.opacity(0.8))

                Button3D("Weiter", color: AppColors.success) {
                    dismissView()
                }
                .frame(width: 200)
            }
            .padding(40)
            .background(
                RoundedRectangle(cornerRadius: 30)
                    .fill(AppColors.darkCard)
                    .shadow(color: AppColors.success.opacity(0.3), radius: 30)
            )
            .scaleEffect(scale)
            .opacity(opacity)
        }
        .onAppear {
            HapticEngine.shared.celebrate()
            showConfetti = true

            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) {
                scale = 1.0
                opacity = 1.0
            }

            // Auto-dismiss confetti
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                showConfetti = false
            }
        }
    }

    private func dismissView() {
        HapticEngine.shared.light()
        withAnimation(.spring()) {
            scale = 0.8
            opacity = 0
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            isShowing = false
        }
    }
}

// MARK: - Milestone View
struct MilestoneView: View {
    let text: String
    @Binding var isShowing: Bool
    @State private var scale: CGFloat = 0.3
    @State private var rotation: Double = -10

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(spacing: 20) {
                Text("🎉")
                    .font(.system(size: 80))

                Text("MEILENSTEIN!")
                    .appFont(size: 28)
                    .foregroundStyle(AppColors.warning)

                Text(text)
                    .appFont(size: 20)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                Button3D("Fantastisch!", color: AppColors.warning) {
                    HapticEngine.shared.success()
                    withAnimation {
                        isShowing = false
                    }
                }
                .frame(width: 200)
            }
            .padding(40)
            .background(
                RoundedRectangle(cornerRadius: 30)
                    .fill(AppColors.darkCard)
                    .shadow(color: AppColors.warning.opacity(0.4), radius: 30)
            )
            .scaleEffect(scale)
            .rotationEffect(.degrees(rotation))
        }
        .onAppear {
            HapticEngine.shared.celebrate()

            withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) {
                scale = 1.0
                rotation = 0
            }
        }
    }
}
