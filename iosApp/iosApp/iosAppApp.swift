//
//  iosAppApp.swift
//  iosApp
//
//  Created by Steve on 2/1/22.
//

import SwiftUI

@main
struct iosAppApp: App {
    @Environment(\.scenePhase) var scenePhase
    
    init() {
        // load configuration assets here
    }
    
    var body: some Scene {
        WindowGroup {
            LogonScreen()
        }.onChange(of: scenePhase) { (newPhase) in
            switch newPhase {
            case .background:
                // close database?
                print("background scenePhase")
            case .inactive:
                print("inactive scenePhase")

            case .active:
                print("active scenePhase")

            @unknown default:
                print("? scenePhase")

            }
        }
    }
}
