import SwiftUI

struct GoalSettingView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var manager: PracticeManager

    @State private var editingDay: Weekday?
    @State private var tempMinutes: Int = 0
    @State private var appeared = false

    var body: some View {
        NavigationStack {
            ZStack {
                BackgroundView()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 8) {
                            Text("🎯")
                                .font(.system(size: 50))
                                .scaleEffect(appeared ? 1.0 : 0.5)
                                .animation(.spring(response: 0.5, dampingFraction: 0.5).delay(0.2), value: appeared)

                            Text("Ziele für diese Woche")
                                .appFont(size: 28)

                            Text("Tippe auf die Minuten, um sie anzupassen")
                                .appFontRegular(size: 14)
                                .opacity(0.6)
                        }
                        .padding(.top, 40)

                        // Day Cards
                        ForEach(Array(Weekday.orderedCases.enumerated()), id: \.element) { index, day in
                            Card3D(shadowColor: manager.goals[day] ?? 0 > 0 ? AppColors.primary.opacity(0.4) : Color.gray.opacity(0.2)) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(day.name)
                                            .appFont(size: 18)

                                        if manager.goals[day] ?? 0 == 0 {
                                            Text("Kein Üben")
                                                .appFontRegular(size: 12)
                                                .opacity(0.5)
                                        }
                                    }
                                    Spacer()

                                    // Clickable minutes button
                                    Button(action: {
                                        HapticEngine.shared.selection()
                                        tempMinutes = manager.goals[day] ?? 0
                                        editingDay = day
                                    }) {
                                        HStack(spacing: 6) {
                                            Text("\(manager.goals[day] ?? 0)")
                                                .appFont(size: 22)
                                            Text("min")
                                                .appFontMedium(size: 14)
                                        }
                                        .foregroundStyle(manager.goals[day] ?? 0 > 0 ? AppColors.primary : .gray)
                                        .padding(.horizontal, 18)
                                        .padding(.vertical, 10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12)
                                                .fill(manager.goals[day] ?? 0 > 0 ? AppColors.primary.opacity(0.15) : Color.gray.opacity(0.1))
                                        )
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                            .opacity(appeared ? 1 : 0)
                            .offset(y: appeared ? 0 : 20)
                            .animation(.spring(response: 0.5, dampingFraction: 0.7).delay(Double(index) * 0.05), value: appeared)
                        }

                        // Total summary
                        Card3D(shadowColor: AppColors.secondary.opacity(0.3)) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Wochengesamt")
                                        .appFontRegular(size: 14)
                                        .opacity(0.6)
                                    Text("\(totalGoalMinutes()) min")
                                        .appFont(size: 24)
                                        .foregroundStyle(AppColors.secondary)
                                }
                                Spacer()
                                Image(systemName: "calendar.badge.clock")
                                    .font(.system(size: 30))
                                    .foregroundStyle(AppColors.secondary)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)

                        // Save Button
                        Button3D("Speichern", icon: "checkmark", color: AppColors.primary) {
                            HapticEngine.shared.success()
                            UserDefaults.standard.set(Date(), forKey: "lastGoalSetDate")
                            dismiss()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)

                        Spacer(minLength: 40)
                    }
                }
            }
            .navigationTitle("")
            .navigationBarHidden(true)
            .sheet(item: $editingDay) { day in
                MinutePickerSheet(
                    day: day,
                    minutes: $tempMinutes,
                    onSave: {
                        manager.goals[day] = tempMinutes
                        editingDay = nil
                    },
                    onCancel: {
                        editingDay = nil
                    }
                )
                .presentationDetents([.height(350)])
            }
            .onAppear {
                withAnimation {
                    appeared = true
                }
            }
        }
    }

    private func totalGoalMinutes() -> Int {
        Weekday.orderedCases.reduce(0) { $0 + (manager.goals[$1] ?? 0) }
    }
}

// MARK: - Minute Picker Sheet
struct MinutePickerSheet: View {
    let day: Weekday
    @Binding var minutes: Int
    let onSave: () -> Void
    let onCancel: () -> Void

    @State private var scale: CGFloat = 1.0

    var body: some View {
        ZStack {
            BackgroundView()

            VStack(spacing: 24) {
                Text(day.name)
                    .appFont(size: 26)
                    .padding(.top, 24)

                // Large stepper
                HStack(spacing: 24) {
                    Button(action: {
                        HapticEngine.shared.light()
                        withAnimation(.spring(response: 0.2)) {
                            scale = 0.9
                        }
                        if minutes >= 5 {
                            minutes -= 5
                        }
                        withAnimation(.spring(response: 0.2).delay(0.1)) {
                            scale = 1.0
                        }
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 60, height: 60)
                            Image(systemName: "minus")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundStyle(.primary)
                        }
                    }

                    VStack(spacing: 4) {
                        Text("\(minutes)")
                            .appFont(size: 64)
                            .scaleEffect(scale)
                            .contentTransition(.numericText())
                        Text("Minuten")
                            .appFontRegular(size: 14)
                            .opacity(0.6)
                    }
                    .frame(width: 140)

                    Button(action: {
                        HapticEngine.shared.light()
                        withAnimation(.spring(response: 0.2)) {
                            scale = 1.1
                        }
                        minutes += 5
                        withAnimation(.spring(response: 0.2).delay(0.1)) {
                            scale = 1.0
                        }
                    }) {
                        ZStack {
                            Circle()
                                .fill(AppColors.primary.opacity(0.2))
                                .frame(width: 60, height: 60)
                            Image(systemName: "plus")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundStyle(AppColors.primary)
                        }
                    }
                }

                // Quick select buttons
                HStack(spacing: 12) {
                    ForEach([0, 10, 20, 30, 45, 60], id: \.self) { value in
                        Button(action: {
                            HapticEngine.shared.selection()
                            withAnimation(.spring(response: 0.3)) {
                                minutes = value
                            }
                        }) {
                            Text("\(value)")
                                .appFontMedium(size: 14)
                                .foregroundStyle(minutes == value ? .white : .primary)
                                .frame(width: 44, height: 36)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(minutes == value ? AppColors.primary : Color.gray.opacity(0.15))
                                )
                        }
                    }
                }

                Spacer()

                // Action buttons
                HStack(spacing: 16) {
                    Button(action: {
                        HapticEngine.shared.light()
                        onCancel()
                    }) {
                        Text("Abbrechen")
                            .appFontMedium(size: 16)
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(Color.gray.opacity(0.15))
                            )
                    }

                    Button3D("OK", color: AppColors.primary) {
                        onSave()
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
        }
    }
}
