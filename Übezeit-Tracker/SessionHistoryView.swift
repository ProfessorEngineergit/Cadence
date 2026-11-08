import SwiftUI

struct SessionHistoryView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var manager: PracticeManager

    @State private var editingSession: PracticeManager.PracticeSession?
    @State private var newDuration: Double = 0

    var body: some View {
        NavigationStack {
            ZStack {
                BackgroundView()

                if manager.sessions.isEmpty {
                    ContentUnavailableView("Keine Einträge", systemImage: "clock.badge.exclamationmark", description: Text("Übe etwas, um hier Einträge zu sehen."))
                } else {
                    List {
                        ForEach(manager.sessions.sorted(by: { $0.date > $1.date })) { session in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(itemFormatter.string(from: session.date))
                                        .appFont(size: 16)
                                    Text(dateFormatter.string(from: session.date))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                Spacer()

                                Text("\(Int(session.duration / 60)) min")
                                    .appFont(size: 18)
                                    .foregroundStyle(.blue)
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    deleteSession(session)
                                } label: {
                                    Label("Löschen", systemImage: "trash")
                                }

                                Button {
                                     startEditing(session)
                                } label: {
                                    Label("Bearbeiten", systemImage: "pencil")
                                }
                                .tint(.orange)
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Verlauf")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fertig") { dismiss() }
                }
            }
            .sheet(item: $editingSession) { session in
                VStack(spacing: 20) {
                    Text("Session bearbeiten")
                        .appFont(size: 20)
                        .padding()

                    Text(dateFormatter.string(from: session.date))

                    HStack {
                        Button(action: { newDuration = max(0, newDuration - 60) }) {
                            Image(systemName: "minus.circle.fill").font(.largeTitle)
                        }

                        Text("\(Int(newDuration / 60)) min")
                            .appFont(size: 30)
                            .frame(width: 120)

                        Button(action: { newDuration += 60 }) {
                            Image(systemName: "plus.circle.fill").font(.largeTitle)
                        }
                    }
                    .padding()

                    Button("Speichern") {
                        manager.updateSession(id: session.id, newDuration: newDuration)
                        editingSession = nil
                    }
                    .buttonStyle(.borderedProminent)
                }
                .presentationDetents([.medium])
            }
        }
    }

    private func deleteSession(_ session: PracticeManager.PracticeSession) {
        if let index = manager.sessions.firstIndex(where: { $0.id == session.id }) {
            manager.sessions.remove(at: index)
        }
    }

    private func startEditing(_ session: PracticeManager.PracticeSession) {
        newDuration = session.duration
        editingSession = session
    }

    private func adjustSession(_ session: PracticeManager.PracticeSession) {
         // Placeholder removed
    }
}

private let itemFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.timeStyle = .short
    return formatter
}()

private let dateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateStyle = .medium
    return formatter
}()
