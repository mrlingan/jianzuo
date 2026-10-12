import SwiftUI

#if os(macOS)
import AppKit

struct NativeWritingEditor: NSViewRepresentable {
    @Binding var text: String
    var fontSize: Double
    var fontStyle: String
    var lineSpacing: Double
    var isEditable: Bool
    @ObservedObject var controller: EditorController
    var ruledPaper: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.autohidesScrollers = true
        scroll.drawsBackground = false
        let editor = RuledTextView()
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
        applyRequest(editor, coordinator: context.coordinator)
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
        applyRequest(editor, coordinator: context.coordinator)
    }

    private func applyRequest(_ editor: NSTextView, coordinator: Coordinator) {
        guard let request = controller.request, request.id != coordinator.handledRequest, !editor.hasMarkedText() else { return }
        coordinator.handledRequest = request.id
        controller.acknowledge(request.id)
        if let insertion = request.insertion, isEditable {
            editor.window?.makeFirstResponder(editor)
            editor.insertText(insertion, replacementRange: editor.selectedRange())
        }
        if let range = request.range {
            let length = (editor.string as NSString).length
            let safe = NSRange(location: min(range.location, length), length: min(range.length, max(0, length - range.location)))
            editor.setSelectedRange(safe)
            editor.scrollRangeToVisible(safe)
        }
    }

    private func applyStyle(_ editor: NSTextView) {
        guard !editor.hasMarkedText() else { return }
        let font: NSFont
        switch fontStyle {
        case "serif": font = NSFont(name: "Songti SC", size: fontSize) ?? .systemFont(ofSize: fontSize)
        case "mono": font = .monospacedSystemFont(ofSize: fontSize, weight: .regular)
        default: font = .systemFont(ofSize: fontSize)
        }
        if editor.font != font { editor.font = font }
        let ink = NSColor(PaperTheme.ink)
        editor.textColor = ink
        editor.insertionPointColor = ink
        if let paper = editor as? RuledTextView { paper.ruled = ruledPaper; paper.needsDisplay = true }
        editor.isEditable = isEditable
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        paragraph.paragraphSpacing = 6
        if editor.defaultParagraphStyle?.lineSpacing != CGFloat(lineSpacing) {
            editor.defaultParagraphStyle = paragraph
            editor.textStorage?.addAttribute(.paragraphStyle, value: paragraph, range: NSRange(location: 0, length: editor.textStorage?.length ?? 0))
        }
        editor.typingAttributes = [.font: font, .foregroundColor: ink, .paragraphStyle: paragraph]
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: NativeWritingEditor
        var handledRequest: UUID?
        init(_ parent: NativeWritingEditor) { self.parent = parent }
        func textDidChange(_ notification: Notification) {
            guard let editor = notification.object as? NSTextView else { return }
            // Keep the IME composition intact; commit to the model when it completes.
            if !editor.hasMarkedText() { parent.text = editor.string }
        }
        func textViewDidChangeSelection(_ notification: Notification) {
            guard let editor = notification.object as? NSTextView else { return }
            let range = editor.selectedRange()
            let value = (editor.string as NSString).substring(with: range)
            let controller = parent.controller
            DispatchQueue.main.async {
                if controller.selection != range { controller.selection = range }
                if controller.selectedText != value { controller.selectedText = value }
            }
        }
    }
}

private final class RuledTextView: NSTextView {
    var ruled = false
    override func draw(_ dirtyRect: NSRect) {
        if ruled, let manager = layoutManager, let container = textContainer {
            NSColor(PaperTheme.ink).withAlphaComponent(0.12).setStroke()
            let visible = dirtyRect.offsetBy(dx: -textContainerOrigin.x, dy: -textContainerOrigin.y)
            let glyphs = manager.glyphRange(forBoundingRect: visible, in: container)
            manager.enumerateLineFragments(forGlyphRange: glyphs) { rect, _, _, _, _ in
                let path = NSBezierPath()
                let y = rect.maxY + self.textContainerOrigin.y + 2
                path.move(to: NSPoint(x: 0, y: y)); path.line(to: NSPoint(x: self.bounds.width, y: y))
                path.lineWidth = 0.5; path.stroke()
            }
        }
        super.draw(dirtyRect)
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
    @ObservedObject var controller: EditorController
    var ruledPaper: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> UITextView {
        // Let UIKit create the connected TextKit 1 stack used by ruled-paper drawing.
        let editor = RuledTextView(usingTextLayoutManager: false)
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
        if editor.text != text && editor.markedTextRange == nil {
            let selection = editor.selectedRange
            editor.text = text
            editor.selectedRange = NSRange(location: min(selection.location, (text as NSString).length), length: 0)
        }
        applyStyle(editor)
        applyRequest(editor, coordinator: context.coordinator)
    }
    private func applyRequest(_ editor: UITextView, coordinator: Coordinator) {
        guard let request = controller.request, request.id != coordinator.handledRequest, editor.markedTextRange == nil else { return }
        coordinator.handledRequest = request.id
        controller.acknowledge(request.id)
        if let insertion = request.insertion, isEditable {
            editor.becomeFirstResponder()
            if let range = editor.selectedTextRange { editor.replace(range, withText: insertion) }
        }
        if let range = request.range {
            let length = (editor.text as NSString).length
            let safe = NSRange(location: min(range.location, length), length: min(range.length, max(0, length - range.location)))
            editor.selectedRange = safe
            editor.scrollRangeToVisible(safe)
        }
    }
    private func applyStyle(_ editor: UITextView) {
        guard editor.markedTextRange == nil else { return }
        let font: UIFont
        switch fontStyle {
        case "serif": font = UIFont(name: "Songti SC", size: fontSize) ?? .systemFont(ofSize: fontSize)
        case "mono": font = .monospacedSystemFont(ofSize: fontSize, weight: .regular)
        default: font = .systemFont(ofSize: fontSize)
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        paragraph.paragraphSpacing = 6
        let ink = UIColor(PaperTheme.ink)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: ink, .paragraphStyle: paragraph]
        if editor.font != font || (editor.typingAttributes[.paragraphStyle] as? NSParagraphStyle)?.lineSpacing != CGFloat(lineSpacing) {
            let selection = editor.selectedRange
            editor.textStorage.addAttributes(attributes, range: NSRange(location: 0, length: editor.textStorage.length))
            editor.font = font
            editor.selectedRange = selection
        }
        editor.typingAttributes = attributes
        editor.textColor = ink
        editor.tintColor = UIColor(PaperTheme.accent)
        if let paper = editor as? RuledTextView { paper.ruled = ruledPaper; paper.setNeedsDisplay() }
        editor.isEditable = isEditable
    }
    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: NativeWritingEditor
        var handledRequest: UUID?
        init(_ parent: NativeWritingEditor) { self.parent = parent }
        func textViewDidChange(_ textView: UITextView) {
            if textView.markedTextRange == nil { parent.text = textView.text }
        }
        func textViewDidChangeSelection(_ textView: UITextView) {
            let range = textView.selectedRange
            let value = (textView.text as NSString).substring(with: range)
            let controller = parent.controller
            DispatchQueue.main.async {
                if controller.selection != range { controller.selection = range }
                if controller.selectedText != value { controller.selectedText = value }
            }
        }
        func scrollViewDidScroll(_ scrollView: UIScrollView) { scrollView.setNeedsDisplay() }
    }
}

private final class RuledTextView: UITextView {
    var ruled = false
    override func draw(_ rect: CGRect) {
        if ruled, let context = UIGraphicsGetCurrentContext() {
            context.setStrokeColor(UIColor(PaperTheme.ink).withAlphaComponent(0.12).cgColor)
            context.setLineWidth(0.5)
            let visible = bounds.offsetBy(dx: -textContainerInset.left, dy: -textContainerInset.top)
            let glyphs = layoutManager.glyphRange(forBoundingRect: visible, in: textContainer)
            layoutManager.enumerateLineFragments(forGlyphRange: glyphs) { fragment, _, _, _, _ in
                let y = fragment.maxY + self.textContainerInset.top + 2
                context.move(to: CGPoint(x: 0, y: y)); context.addLine(to: CGPoint(x: self.bounds.width, y: y))
            }
            context.strokePath()
        }
        super.draw(rect)
    }
}
#endif
