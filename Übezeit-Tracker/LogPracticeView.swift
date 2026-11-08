import SwiftUI

struct LogPracticeView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var manager: PracticeManager

    @State private var selectedDate = Date()
    @State private var durationMinutes: Int = 0
    @State private var showConfirmation = false
    @State private var scale: CGFloat = 1.0
    @State private var appeared = false
    @State private var isEditingExisting = false
    @State private var confirmationMessage = ""

    var body: some View {
        NavigationStack {
            ZStack {
                BackgroundView()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 24) {
                        // Header
                        VStack(spacing: 8) {
                            Text(isEditingExisting ? "✏️" : "⏰")
                                .font(.system(size: 50))
                                .scaleEffect(appeared ? 1.0 : 0.5)
                                .animation(.spring(response: 0.5, dampingFraction: 0.5).delay(0.2), value: appeared)

                            Text(isEditingExisting ? "Eintrag bearbeiten" : "Übezeit nachtragen")
                                .appFont(size: 28)

                            Text(isEditingExisting ? "Ändere oder lösche den Eintrag für diesen Tag" : "Vergessen den Timer zu starten? Kein Problem!")
                                .appFontRegular(size: 14)
                                .opacity(0.6)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 40)

                        // Date Picker
                        Card3D(shadowColor: AppColors.secondary.opacity(0.4)) {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Image(systemName: "calendar")
                                        .foregroundStyle(AppColors.secondary)
                                    Text("Datum")
                                        .appFont(size: 16)
                                }

                                DatePicker("", selection: $selectedDate, in: ...Date(), displayedComponents: .date)
                                    .datePickerStyle(.graphical)
                                    .labelsHidden()
                                    .tint(AppColors.primary)
                                    .onChange(of: selectedDate) { oldValue, newValue in
                                        loadExistingDataForDate(newValue)
                                    }
                            }
                        }
                        .padding(.horizontal, 20)
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 20)
                        .animation(.spring(response: 0.5, dampingFraction: 0.7).delay(0.1), value: appeared)

                        // Duration Picker
                        Card3D(shadowColor: AppColors.primary.opacity(0.4)) {
                            VStack(spacing: 20) {
                                HStack {
                                    Image(systemName: "clock.fill")
                                        .foregroundStyle(AppColors.primary)
                                    Text("Dauer")
                                        .appFont(size: 16)
                                    Spacer()
                                }

                                HStack(spacing: 24) {
                                    Button(action: {
                                        HapticEngine.shared.light()
                                        withAnimation(.spring(response: 0.2)) {
                                            scale = 0.9
                                        }
                                        if durationMinutes > 0 {
                                            durationMinutes -= 5
                                            if durationMinutes < 0 { durationMinutes = 0 }
                                        }
                                        withAnimation(.spring(response: 0.2).delay(0.1)) {
                                            scale = 1.0
                                        }
                                    }) {
                                        ZStack {
                                            Circle()
                                                .fill(Color.gray.opacity(0.2))
                                                .frame(width: 56, height: 56)
                                            Image(systemName: "minus")
                                                .font(.system(size: 22, weight: .bold))
                                                .foregroundStyle(.primary)
                                        }
                                    }

                                    VStack(spacing: 4) {
                                        Text("\(durationMinutes)")
                                            .appFont(size: 56)
                                            .scaleEffect(scale)
                                            .contentTransition(.numericText())
                                            .foregroundStyle(AppColors.primary)
                                        Text("Minuten")
                                            .appFontRegular(size: 14)
                                            .opacity(0.6)
                                    }
                                    .frame(width: 130)

                                    Button(action: {
                                        HapticEngine.shared.light()
                                        withAnimation(.spring(response: 0.2)) {
                                            scale = 1.1
                                        }
                                        durationMinutes += 5
                                        withAnimation(.spring(response: 0.2).delay(0.1)) {
                                            scale = 1.0
                                        }
                                    }) {
                                        ZStack {
                                            Circle()
                                                .fill(AppColors.primary.opacity(0.2))
                                                .frame(width: 56, height: 56)
                                            Image(systemName: "plus")
                                                .font(.system(size: 22, weight: .bold))
                                                .foregroundStyle(AppColors.primary)
                                        }
                                    }
                                }

                                // Quick select buttons
                                HStack(spacing: 8) {
                                    ForEach([0, 5, 10, 15, 20, 30, 45], id: \.self) { value in
                                        Button(action: {
                                            HapticEngine.shared.selection()
                                            withAnimation(.spring(response: 0.3)) {
                                                durationMinutes = value
                                            }
                                        }) {
                                            Text("\(value)")
                                                .appFontMedium(size: 13)
                                                .foregroundStyle(durationMinutes == value ? .white : .primary)
                                                .frame(width: 38, height: 34)
                                                .background(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .fill(durationMinutes == value ? AppColors.primary : Color.gray.opacity(0.15))
                                                )
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 20)
                        .animation(.spring(response: 0.5, dampingFraction: 0.7).delay(0.2), value: appeared)

                        // Save Button
                        Button3D(
                            isEditingExisting && durationMinutes == 0 ? "Eintrag löschen" : "Speichern",
                            icon: isEditingExisting && durationMinutes == 0 ? "trash" : "checkmark",
                            color: (durationMinutes > 0 || isEditingExisting) ? (isEditingExisting && durationMinutes == 0 ? AppColors.error : AppColors.primary) : .gray
                        ) {
                            if durationMinutes > 0 || isEditingExisting {
                                saveSession()
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                        .opacity(appeared ? 1 : 0)
                        .animation(.spring(response: 0.5, dampingFraction: 0.7).delay(0.3), value: appeared)

                        // Cancel button
                        Button(action: {
                            HapticEngine.shared.light()
                            dismiss()
                        }) {
                            Text("Abbrechen")
                                .appFontMedium(size: 16)
                                .foregroundStyle(.primary)
                                .opacity(0.7)
                        }
                        .padding(.top, 8)

                        Spacer(minLength: 40)
                    }
                }
            }
            .navigationTitle("")
            .navigationBarHidden(true)
            .overlay {
                // Confirmation overlay
                if showConfirmation {
                    ZStack {
                        Color.black.opacity(0.5).ignoresSafeArea()

                        VStack(spacing: 20) {
                            ZStack {
                                Circle()
                                    .fill(durationMinutes == 0 ? AppColors.error.opacity(0.2) : AppColors.success.opacity(0.2))
                                    .frame(width: 100, height: 100)

                                Image(systemName: durationMinutes == 0 ? "trash.fill" : "checkmark.circle.fill")
                                    .resizable()
                                    .frame(width: 70, height: 70)
                                    .foregroundStyle(durationMinutes == 0 ? AppColors.error : AppColors.success)
                                    .symbolEffect(.bounce, value: showConfirmation)
                            }

                            Text(durationMinutes == 0 ? "Gelöscht!" : "Gespeichert!")
                                .appFont(size: 28)
                                .foregroundStyle(.white)

                            Text(confirmationMessage)
                                .appFontRegular(size: 16)
                                .foregroundStyle(.white.opacity(0.8))
                        }
                        .padding(40)
                        .background(
                            RoundedRectangle(cornerRadius: 30)
                                .fill(AppColors.darkCard)
                                .shadow(color: (durationMinutes == 0 ? AppColors.error : AppColors.success).opacity(0.3), radius: 20)
                        )
                    }
                }
            }
            .onAppear {
                withAnimation {
                    appeared = true
                }
                // Lade bestehende Daten für heute
                loadExistingDataForDate(selectedDate)
            }
        }
    }

    private func loadExistingDataForDate(_ date: Date) {
        let existingMinutes = manager.totalMinutesForDate(date)
        withAnimation(.spring(response: 0.3)) {
            durationMinutes = existingMinutes
            isEditingExisting = existingMinutes > 0
        }
    }

    private func saveSession() {
        HapticEngine.shared.celebrate()

        // Speichere die aktuelle Anzahl für die Bestätigungsnachricht
        let savedDuration = durationMinutes
        let wasEditing = isEditingExisting

        // Verwende die neue Funktion die aktualisiert oder löscht
        manager.updateOrCreateSessionForDate(selectedDate, durationMinutes: durationMinutes)

        // Setze die Bestätigungsnachricht
        if savedDuration == 0 {
            confirmationMessage = "Eintrag gelöscht"
        } else if wasEditing {
            confirmationMessage = "Auf \(savedDuration) Minuten geändert"
        } else {
            confirmationMessage = "\(savedDuration) Minuten hinzugefügt"
        }

        // Show confirmation briefly, then dismiss
        withAnimation(.spring()) {
            showConfirmation = true
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            dismiss()
        }
    }
}
