//
//  AsyncButton.swift
//  Displays a button with text or an Icon/image. Button action is async,
//  and button is disabled while async operation is running, if that operation
//  runs for longer than 100ms.
//  IosKmpTest
//
//  Created by Steve on 1/24/22.
//

import SwiftUI

struct AsyncButton<Label: View>: View {
    enum ActionOption: CaseIterable {
        case disableButton
        case showProgressView
    }
    
    var action: () async -> Void
    var actionOptions = Set(ActionOption.allCases)
    @ViewBuilder var label: () -> Label

    @State private var isDisabled = false
    @State private var showProgressView = false

    var body: some View {
        Button(
            action: {
                if actionOptions.contains(.disableButton) {
                    isDisabled = true
                }
            
                Task {
                    // crank of a subtask that only waits 100ms. If async action takes longer, then change state to show PropgressView
                    var progressViewTask: Task<Void, Error>?

                    if actionOptions.contains(.showProgressView) {
                        progressViewTask = Task {
                            try await Task.sleep(nanoseconds: 100_000_000)
                            showProgressView = true
                        }
                    }

                    await action()
                    progressViewTask?.cancel()

                    isDisabled = false
                    showProgressView = false
                }
            },
            label: {
                ZStack {
                    label().opacity(showProgressView ? 0 : 1)

                    if showProgressView {
                        ProgressView()
                    }
                }
            }
        )
        .disabled(isDisabled)
    }
}

extension AsyncButton where Label == Text {
    init(_ label: String,
         actionOptions: Set<ActionOption> = Set(ActionOption.allCases),
         action: @escaping () async -> Void) {
        self.init(action: action) {
            Text(label)
        }
    }
}

extension AsyncButton where Label == Image {
    init(systemImageName: String,
         actionOptions: Set<ActionOption> = Set(ActionOption.allCases),
         action: @escaping () async -> Void) {
        self.init(action: action) {
            Image(systemName: systemImageName)
        }
    }
}

struct AsyncButton_Previews: PreviewProvider {
    static var previews: some View {
        AsyncButton("testText") {
            do {
                try await Task.sleep(nanoseconds: 500_000_000)
            } catch {
            }
        }
    }
}
