import SwiftUI

#if os(macOS)
import AppKit

struct NativeWritingEditor: NSViewRepresentable {
    @Binding var text: String
    var fontSize: Double
    var fontStyle: String
    var lineSpacing: Double
    var isEditable: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.autohidesScrollers = true
        scroll.drawsBackground = false
        let editor = NSTextView()
        editor.delegate = context.coordinator
        editor.isRichText = false
        editor.allowsUndo = true
        editor.isAutomaticQuoteSubstitutionEnabled = false
        editor.isAutomaticDashSubstitutionEnabled = false
        editor.isAutomaticSpellingCorrectionEnabled = false
        editor.isContinuousSpellCheckingEnabled = true
        editor.drawsBackground = false
        editor.isVerticallyResizable = true
        editor.isHorizontallyResizable = false
        editor.autoresizingMask = [.width]
        editor.textContainer?.widthTracksTextView = true
        editor.textContainer?.lineFragmentPadding = 0
        editor.textContainerInset = NSSize(width: 0, height: 24)
        editor.minSize = NSSize(width: 0, height: 0)
        editor.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        editor.string = text
        editor.setAccessibilityLabel("文稿正文")
        scroll.documentView = editor
        applyStyle(editor)
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        context.coordinator.parent = self
        guard let editor = scroll.documentView as? NSTextView else { return }
        if editor.string != text && !editor.hasMarkedText() {
            let selection = editor.selectedRange()
            editor.string = text
            editor.setSelectedRange(NSRange(location: min(selection.location, (text as NSString).length), length: 0))
        }
        applyStyle(editor)
    }

    private func applyStyle(_ editor: NSTextView) {
        let font: NSFont
        switch fontStyle {
        case "serif": font = NSFont(name: "Songti SC", size: fontSize) ?? .systemFont(ofSize: fontSize)
        case "mono": font = .monospacedSystemFont(ofSize: fontSize, weight: .regular)
        default: font = .systemFont(ofSize: fontSize)
        }
        if editor.font != font { editor.font = font }
        editor.textColor = .labelColor
        editor.insertionPointColor = .labelColor
        editor.isEditable = isEditable
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        paragraph.paragraphSpacing = 6
        if editor.defaultParagraphStyle?.lineSpacing != CGFloat(lineSpacing) {
            editor.defaultParagraphStyle = paragraph
            editor.textStorage?.addAttribute(.paragraphStyle, value: paragraph, range: NSRange(location: 0, length: editor.textStorage?.length ?? 0))
        }
        editor.typingAttributes = [.font: font, .foregroundColor: NSColor.labelColor, .paragraphStyle: paragraph]
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: NativeWritingEditor
        init(_ parent: NativeWritingEditor) { self.parent = parent }
        func textDidChange(_ notification: Notification) {
            guard let editor = notification.object as? NSTextView else { return }
            // Keep the IME composition intact; commit to the model when it completes.
            if !editor.hasMarkedText() { parent.text = editor.string }
        }
    }
}

#else
import UIKit

struct NativeWritingEditor: UIViewRepresentable {
    @Binding var text: String
    var fontSize: Double
    var fontStyle: String
    var lineSpacing: Double
    var isEditable: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> UITextView {
        let editor = UITextView()
        editor.delegate = context.coordinator
        editor.backgroundColor = .clear
        editor.textContainerInset = UIEdgeInsets(top: 24, left: 0, bottom: 32, right: 0)
        editor.textContainer.lineFragmentPadding = 0
        editor.alwaysBounceVertical = true
        editor.keyboardDismissMode = .interactive
        editor.smartQuotesType = .no
        editor.smartDashesType = .no
        editor.text = text
        editor.accessibilityLabel = "文稿正文"
        applyStyle(editor)
        return editor
    }
    func updateUIView(_ editor: UITextView, context: Context) {
        context.coordinator.parent = self
        if editor.text != text && editor.markedTextRange == nil { editor.text = text }
        applyStyle(editor)
    }
    private func applyStyle(_ editor: UITextView) {
        let font: UIFont
        switch fontStyle {
        case "serif": font = UIFont(name: "Songti SC", size: fontSize) ?? .systemFont(ofSize: fontSize)
        case "mono": font = .monospacedSystemFont(ofSize: fontSize, weight: .regular)
        default: font = .systemFont(ofSize: fontSize)
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        paragraph.paragraphSpacing = 6
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.label, .paragraphStyle: paragraph]
        if editor.font != font || (editor.typingAttributes[.paragraphStyle] as? NSParagraphStyle)?.lineSpacing != CGFloat(lineSpacing) {
            let selection = editor.selectedRange
            editor.textStorage.addAttributes(attributes, range: NSRange(location: 0, length: editor.textStorage.length))
            editor.font = font
            editor.selectedRange = selection
        }
        editor.typingAttributes = attributes
        editor.textColor = .label
        editor.isEditable = isEditable
    }
    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: NativeWritingEditor
        init(_ parent: NativeWritingEditor) { self.parent = parent }
        func textViewDidChange(_ textView: UITextView) {
            if textView.markedTextRange == nil { parent.text = textView.text }
        }
    }
}
#endif
