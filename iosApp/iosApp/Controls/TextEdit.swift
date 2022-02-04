//
//  TextEdit.swift
//  IosKmpTest
//
//  Created by Steve on 1/24/22.
//

import SwiftUI

struct HorizontalLineShape: Shape {

    func path(in rect: CGRect) -> Path {

        let fill = CGRect(x: 0, y: 0, width: rect.size.width, height: rect.size.height)
        var path = Path()
        path.addRoundedRect(in: fill, cornerSize: CGSize(width: 2, height: 2))

        return path
    }
}

struct HorizontalLine: View {

    private var color: Color?   = nil
    private var height: CGFloat = 1.0

    init(color: Color, height: CGFloat = 1.0) {
        self.color  = color
        self.height = height
    }

    var body: some View {
        HorizontalLineShape().fill(self.color!).frame(minWidth: 0, maxWidth: .infinity, minHeight: height, maxHeight: height)
    }
}

struct TextEdit: View {
    @State var text: String = ""
    @State var error: String = ""
    private var label = ""
    private var hint = ""
    private let lineThickness = CGFloat(2.0)

    init(label: String, hint: String) {
        self.label = label
        self.hint = hint
    }

    var body: some View {
        VStack {
            TextField(hint, text: $text)
                .foregroundColor(Theme.current.colors.onBackground)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .shadow(radius: 6)
            HorizontalLine(color: Theme.current.colors.surface)
            if !error.isEmpty {
                Text("\(error)")
                    .fontWeight(.bold)
                    .foregroundColor(Theme.current.colors.error)
                    .padding(.leading, Theme.current.margin)
                    .font(.caption)
                    
            }
        }
        .padding(.bottom, lineThickness)
    }

}
