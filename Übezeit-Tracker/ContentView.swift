//
//  ContentView.swift
//  Übezeit-Tracker
//
//  Created by Bahrian Novotny on 10.02.26.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var manager: PracticeManager

    var body: some View {
        DashboardView(manager: manager)
    }
}

#Preview {
    ContentView()
        .environmentObject(PracticeManager())
}
