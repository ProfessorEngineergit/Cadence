//
//  U_bezeit_TrackerApp.swift
//  Übezeit-Tracker
//
//  Created by Bahrian Novotny on 10.02.26.
//

import SwiftUI

@main
struct U_bezeit_TrackerApp: App {
    @StateObject var manager = PracticeManager()
    @ObservedObject var themeManager = ThemeManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(manager)
                .preferredColorScheme(themeManager.isDarkMode ? .dark : .light)
                .onAppear {
                    ThemeManager.shared.applyTheme()
                }
        }
    }
}
