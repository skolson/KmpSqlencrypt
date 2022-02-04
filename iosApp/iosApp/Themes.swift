//
//  Themes.swift
//  IosKmpTest
//
//  Contains color schemes, typography, margins and other UI-wide configuration
//  Created by Steve on 1/24/22.
//

import SwiftUI

struct Colors {
    private(set) var error = Color.red
    private(set) var onBackground = Color.white
    private(set) var background = Color.black
    private(set) var surface = Color.gray
    
    init(error: Color, onBackground: Color, background: Color, surface: Color) {
        self.error = error
        
    }

    static let darkTheme = Colors(
        error: Color.red,
        onBackground: Color.white,
        background: Color.black,
        surface: Color.gray
    )
    
    static let lightTheme = Colors(
        error: Color.red,
        onBackground: Color.black,
        background: Color("lightBlue"),
        surface: Color.gray
    )
}

struct Theme {
    static var current = Theme(colors: Colors.lightTheme)
    private(set) var colors = Colors.lightTheme
    
    var margin = CGFloat(10.0)
    
    init(colors: Colors) {
        self.colors = colors
    }
    
}
