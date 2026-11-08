import SwiftUI
import Combine

enum Weekday: Int, CaseIterable, Codable {
    case sunday = 1, monday, tuesday, wednesday, thursday, friday, saturday

    var name: String {
        switch self {
        case .monday: return "Montag"
        case .tuesday: return "Dienstag"
        case .wednesday: return "Mittwoch"
        case .thursday: return "Donnerstag"
        case .friday: return "Freitag"
        case .saturday: return "Samstag"
        case .sunday: return "Sonntag"
        }
    }

    var shortName: String {
        switch self {
        case .monday: return "Mo"
        case .tuesday: return "Di"
        case .wednesday: return "Mi"
        case .thursday: return "Do"
        case .friday: return "Fr"
        case .saturday: return "Sa"
        case .sunday: return "So"
        }
    }

    static var orderedCases: [Weekday] {
        [.monday, .tuesday, .wednesday, .thursday, .friday, .saturday, .sunday]
    }
}

class PracticeManager: ObservableObject {
    // Wochenziel: 100 Minuten
    static let weeklyGoalMinutes: Int = 100

    @Published var goals: [Weekday: Int] = [:]
    @Published var sessions: [PracticeSession] = []
    @Published var isTimerRunning: Bool = false
    @Published var timerStartTime: Date?
    @Published var currentElapsed: TimeInterval = 0
    @Published var totalLifetimeMinutes: Int = 0

    private var timer: AnyCancellable?

    struct PracticeSession: Identifiable, Codable {
        var id = UUID()
        var date: Date
        var duration: TimeInterval // in seconds

        var durationMinutes: Int {
            Int(duration / 60)
        }
    }

    init() {
        loadData()
        setupDefaultGoalsIfNeeded()
        calculateLifetimeMinutes()

        // Restore timer state if app was killed
        if let savedStartTime = UserDefaults.standard.object(forKey: "timerStartTime") as? Date {
            self.timerStartTime = savedStartTime
            self.isTimerRunning = true
            startTimerTick()
        }
    }

    func setupDefaultGoalsIfNeeded() {
        if goals.isEmpty {
            // Default model
            goals[.wednesday] = 30
            goals[.friday] = 30
            goals[.saturday] = 10
            goals[.sunday] = 30
            // Others 0 implicitly
            for day in Weekday.orderedCases {
                if goals[day] == nil {
                    goals[day] = 0
                }
            }
        }
    }

    func toggleTimer() {
        if isTimerRunning {
            stopTimer()
        } else {
            startTimer()
        }
    }

    func startTimer() {
        isTimerRunning = true
        timerStartTime = Date()
        UserDefaults.standard.set(timerStartTime, forKey: "timerStartTime")
        startTimerTick()
    }

    private func startTimerTick() {
        timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            guard let self = self, let startTime = self.timerStartTime else { return }
            self.currentElapsed = Date().timeIntervalSince(startTime)
        }
    }

    func stopTimer() {
        guard let startTime = timerStartTime else { return }
        let duration = Date().timeIntervalSince(startTime)

        saveSession(date: startTime, duration: duration)

        isTimerRunning = false
        timerStartTime = nil
        currentElapsed = 0
        timer?.cancel()
        UserDefaults.standard.removeObject(forKey: "timerStartTime")
    }

    func saveSession(date: Date, duration: TimeInterval) {
        let newSession = PracticeSession(date: date, duration: duration)
        sessions.append(newSession)
        saveData()
        calculateLifetimeMinutes()
    }

    func updateSession(id: UUID, newDuration: TimeInterval) {
        if let index = sessions.firstIndex(where: { $0.id == id }) {
            sessions[index].duration = newDuration
            saveData()
            calculateLifetimeMinutes()
        }
    }

    func deleteSession(id: UUID) {
        sessions.removeAll { $0.id == id }
        saveData()
        calculateLifetimeMinutes()
    }

    func sessionsForDate(_ date: Date) -> [PracticeSession] {
        let calendar = Calendar.current
        return sessions.filter { calendar.isDate($0.date, inSameDayAs: date) }
    }

    func totalMinutesForDate(_ date: Date) -> Int {
        let daySessions = sessionsForDate(date)
        let totalSeconds = daySessions.reduce(0) { $0 + $1.duration }
        return Int(totalSeconds / 60)
    }

    func updateOrCreateSessionForDate(_ date: Date, durationMinutes: Int) {
        let existingSessions = sessionsForDate(date)

        if durationMinutes == 0 {
            // Lösche alle Sessions für diesen Tag
            for session in existingSessions {
                deleteSession(id: session.id)
            }
        } else if let firstSession = existingSessions.first {
            // Aktualisiere die erste Session und lösche den Rest
            let newDuration = TimeInterval(durationMinutes * 60)
            updateSession(id: firstSession.id, newDuration: newDuration)

            // Lösche weitere Sessions falls vorhanden
            for session in existingSessions.dropFirst() {
                deleteSession(id: session.id)
            }
        } else {
            // Erstelle neue Session
            addManualSession(date: date, durationMinutes: durationMinutes)
        }
    }

    // MARK: - Persistence
    private func saveData() {
        if let encoded = try? JSONEncoder().encode(sessions) {
            UserDefaults.standard.set(encoded, forKey: "savedSessions")
        }

        let goalData = Dictionary(uniqueKeysWithValues: goals.map { ($0.key.rawValue, $0.value) })
        if let encodedGoals = try? JSONEncoder().encode(goalData) {
            UserDefaults.standard.set(encodedGoals, forKey: "savedGoals")
        }
    }

    private func loadData() {
        if let data = UserDefaults.standard.data(forKey: "savedSessions"),
           let decoded = try? JSONDecoder().decode([PracticeSession].self, from: data) {
            sessions = decoded
        }

        if let data = UserDefaults.standard.data(forKey: "savedGoals"),
           let decoded = try? JSONDecoder().decode([Int: Int].self, from: data) {
            goals = Dictionary(uniqueKeysWithValues: decoded.compactMap {
                guard let day = Weekday(rawValue: $0.key) else { return nil }
                return (day, $0.value)
            })
        }
    }

    // MARK: - Helpers

    func currentWeekday() -> Weekday {
        let weekdayInt = Calendar.current.component(.weekday, from: Date())
        return Weekday(rawValue: weekdayInt) ?? .monday
    }

    func todayGoalMinutes() -> Int {
        return goals[currentWeekday()] ?? 0
    }

    func practicedTodayMinutes() -> Int {
        let todaySessions = sessions.filter { Calendar.current.isDate($0.date, inSameDayAs: Date()) }
        let totalSeconds = todaySessions.reduce(0) { $0 + $1.duration }
        return Int(totalSeconds / 60)
    }

    func practicedMinutes(for weekday: Weekday) -> Int {
        let calendar = Calendar.current
        let today = Date()

        guard let weekStart = calendar.dateInterval(of: .weekOfYear, for: today)?.start else { return 0 }

        let offset = weekday.rawValue - calendar.component(.weekday, from: weekStart)
        guard let targetDate = calendar.date(byAdding: .day, value: offset, to: weekStart) else { return 0 }

        let daySessions = sessions.filter { calendar.isDate($0.date, inSameDayAs: targetDate) }
        let totalSeconds = daySessions.reduce(0) { $0 + $1.duration }
        return Int(totalSeconds / 60)
    }

    func totalPracticedThisWeekMinutes() -> Int {
        let calendar = Calendar.current
        let today = Date()
        guard let weekStart = calendar.dateInterval(of: .weekOfYear, for: today)?.start,
              let weekEnd = calendar.dateInterval(of: .weekOfYear, for: today)?.end else { return 0 }

        let weekSessions = sessions.filter { $0.date >= weekStart && $0.date < weekEnd }
        let totalSeconds = weekSessions.reduce(0) { $0 + $1.duration }
        return Int(totalSeconds / 60)
    }

    func progressForToday() -> Double {
        let goal = todayGoalMinutes()
        guard goal > 0 else { return 0 }
        let practiced = practicedTodayMinutes()
        return min(Double(practiced) / Double(goal), 1.0)
    }

    func progressForWeek() -> Double {
        let goal = Self.weeklyGoalMinutes
        guard goal > 0 else { return 0 }
        let practiced = totalPracticedThisWeekMinutes()
        return min(Double(practiced) / Double(goal), 1.0)
    }

    func addManualSession(date: Date, durationMinutes: Int) {
        let session = PracticeSession(date: date, duration: TimeInterval(durationMinutes * 60))
        sessions.append(session)
        saveData()
        calculateLifetimeMinutes()
    }

    func isMonday() -> Bool {
        Calendar.current.component(.weekday, from: Date()) == Weekday.monday.rawValue
    }

    /// Calculate practice saldo (surplus or debt)
    func calculatePracticeSaldo() -> Int {
        let currentDayIndex = Weekday.orderedCases.firstIndex(of: currentWeekday()) ?? 0

        var totalGoal = 0
        var totalPracticed = 0

        for i in 0..<currentDayIndex {
            let day = Weekday.orderedCases[i]
            let goal = goals[day] ?? 0
            let practiced = practicedMinutes(for: day)

            totalGoal += goal
            totalPracticed += practiced
        }

        return totalPracticed - totalGoal
    }

    func hasDayPassed(_ weekday: Weekday) -> Bool {
        let currentDayIndex = Weekday.orderedCases.firstIndex(of: currentWeekday()) ?? 0
        let checkDayIndex = Weekday.orderedCases.firstIndex(of: weekday) ?? 0
        return checkDayIndex < currentDayIndex
    }

    func isToday(_ weekday: Weekday) -> Bool {
        return weekday == currentWeekday()
    }

    func calculateLifetimeMinutes() {
        let totalSeconds = sessions.reduce(0) { $0 + $1.duration }
        totalLifetimeMinutes = Int(totalSeconds / 60)
    }

    /// Get a motivational message based on current progress
    func getMotivationalMessage() -> String {
        let progress = progressForToday()
        let saldo = calculatePracticeSaldo()
        let todayGoal = todayGoalMinutes()

        if todayGoal > 0 && practicedTodayMinutes() >= todayGoal {
            return ["Super gemacht! 🎉", "Ziel erreicht! 💪", "Weiter so! ⭐️"].randomElement() ?? "Toll!"
        }

        if todayGoal == 0 {
            if practicedTodayMinutes() > 0 {
                return "Bonus-Übezeit! Extra fleißig! 🌟"
            }
            return "Heute ist frei – genieß es! 😊"
        }

        if saldo < -30 {
            return "Zeit aufzuholen! Du schaffst das! 💪"
        } else if saldo < 0 {
            return "Kleine Schuld – leicht aufholbar! 🎯"
        }

        if progress == 0 {
            return ["Los geht's! 🎵", "Zeit zu üben! 🎹", "Lass uns starten! 🎼"].randomElement() ?? "Start!"
        }

        if progress < 0.5 {
            return "Guter Anfang! Weiter so! 🌱"
        } else if progress < 1.0 {
            return "Fast geschafft! Nur noch ein bisschen! 🏃"
        }

        return "Üben macht den Meister! 🎶"
    }
}

// MARK: - Weekday Identifiable Extension
extension Weekday: Identifiable {
    var id: Int { rawValue }
}
