import AppKit
import ImageIO

// Masters are exported from Design/AppIcon/Jianzuo-Icon-Design.sketch.
// swift Scripts/make_icon.swift output.png [--macos] [--size 256]
// swift Scripts/make_icon.swift --install
let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let arguments = Array(CommandLine.arguments.dropFirst())
func master(_ macOS: Bool) throws -> CGImage {
    let url = root.appendingPathComponent("Design/AppIcon/Exports/AppIcon-\(macOS ? "macOS" : "iOS").png")
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
          image.width == 1024, image.height == 1024 else {
        throw NSError(domain: "JianzuoIcon", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing or invalid 1024px Sketch export: \(url.path)"])
    }
    return image
}
func write(_ image: CGImage, size: Int, to url: URL, opaque: Bool) throws {
    guard size > 0, size <= 1024,
          let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
            bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue) else {
        throw NSError(domain: "JianzuoIcon", code: 2, userInfo: [NSLocalizedDescriptionKey: "Icon size must be between 1 and 1024"])
    }
    context.interpolationQuality = .high
    context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
    try bitmap.representation(using: .png, properties: [:])!.write(to: url, options: .atomic)
}
if arguments == ["--normalize-masters"] {
    // SVG groups imported by Sketch inherit the presentation's frame size.
    // Keep their original top-left 1024px icon bounds, including Mac transparency.
    for name in ["iOS", "macOS"] {
        let url = root.appendingPathComponent("Design/AppIcon/Exports/AppIcon-\(name).png")
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
              image.width >= 1024, image.height >= 1024,
              let cropped = image.cropping(to: CGRect(x: 0, y: 0, width: 1024, height: 1024)) else {
            fatalError("Invalid Sketch export: \(url.path)")
        }
        try write(cropped, size: 1024, to: url, opaque: name == "iOS")
    }
} else if arguments == ["--install"] {
    let directory = root.appendingPathComponent("Jianzuo/Resources/Assets.xcassets/AppIcon.appiconset")
    let json = try JSONSerialization.jsonObject(with: Data(contentsOf: directory.appendingPathComponent("Contents.json"))) as! [String: Any]
    let ios = try master(false), mac = try master(true)
    for item in json["images"] as! [[String: String]] {
        guard let filename = item["filename"], let dimensions = item["size"],
              let base = Int(dimensions.split(separator: "x")[0]) else { continue }
        let isMac = item["idiom"] == "mac"
        let scale = Int((item["scale"] ?? "1x").dropLast()) ?? 1
        try write(isMac ? mac : ios, size: base * scale, to: directory.appendingPathComponent(filename), opaque: !isMac)
        print("Updated \(filename) (\(base * scale)px)")
    }
} else if let destination = arguments.first, !destination.hasPrefix("--") {
    let isMac = arguments.contains("--macos")
    var size = 1024
    if let index = arguments.firstIndex(of: "--size"), index + 1 < arguments.count {
        size = Int(arguments[index + 1]) ?? 0
    }
    try write(master(isMac), size: size, to: URL(fileURLWithPath: destination), opaque: !isMac)
} else {
    fputs("Usage: swift Scripts/make_icon.swift output.png [--macos] [--size N]\n       swift Scripts/make_icon.swift --install\n", stderr)
    exit(1)
}
