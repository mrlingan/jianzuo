import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    static let jianzuoBackup = UTType(exportedAs: "com.jianzuo.apple-backup", conformingTo: .json)
}
struct BackupFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.jianzuoBackup, .json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct BackupView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false
    @State private var exporting = false
    @State private var exportData = Data()
    @State private var preview: LibrarySnapshot?
    @State private var previewName = ""
    @State private var includeStatistics = false
    @State private var confirmMerge = false
    @State private var message: String?
    @State private var query = ""
    @State private var recovery: RecoveryEntry?

    var body: some View {
        NavigationStack {
            List {
                Section("完整备份") {
                    Button("导出校验备份…", systemImage: "square.and.arrow.up") {
                        do { library.saveNow(); exportData = try BackupCodec.encode(library.snapshot); exporting = true }
                        catch { message = error.localizedDescription }
                    }
                    Button("选择备份恢复…", systemImage: "square.and.arrow.down") { importing = true }
                    Text("备份包含文稿、废纸篓、历史、笔记本、标签、速记和每日字数。恢复时合并为新文稿，不覆盖现有内容。Apple 与安卓的备份格式独立，跨平台使用 Markdown / TXT。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if let preview {
                    Section("恢复预览 · \(previewName)") {
                        LabeledContent("文稿 / 废纸篓", value: "\(preview.documents.filter { $0.deletedAt == nil }.count) / \(preview.documents.filter { $0.deletedAt != nil }.count)")
                        LabeledContent("笔记本", value: "\(preview.notebooks.count)")
                        Toggle("同时恢复每日字数", isOn: $includeStatistics)
                        Text("同一天的统计取较大值，避免重复累加。重复导入会再生成一份文稿副本。").font(.footnote).foregroundStyle(.secondary)
                        DisclosureGroup("查看文稿") {
                            ForEach(preview.documents.prefix(100)) { document in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(document.displayTitle)
                                    Text("\(document.notebook.rawValue) · \(document.wordCount) 字\(document.deletedAt == nil ? "" : " · 废纸篓")").font(.caption).foregroundStyle(.secondary)
                                    Text(document.body.prefix(160)).font(.footnote).foregroundStyle(.secondary).lineLimit(3)
                                }
                            }
                        }
                        Button("合并恢复") { confirmMerge = true }
                        Button("取消预览") { self.preview = nil }
                    }
                }
                Section("本机自动备份") {
                    Text("文稿库变化后，保存前保留上一份内容；每分钟最多一份，轮换保留最近 10 份。这些备份仍在本机，重要内容请另存。")
                        .font(.footnote).foregroundStyle(.secondary)
                    ForEach(library.automaticBackups, id: \.path) { url in
                        Button {
                            load(url)
                        } label: {
                            let stamp = Double(url.lastPathComponent.split(separator: "-").first ?? "") ?? 0
                            Label(Date(timeIntervalSince1970: stamp / 1000).formatted(date: .abbreviated, time: .standard), systemImage: "clock.arrow.circlepath")
                        }
                    }
                    if library.automaticBackups.isEmpty { Text("编辑并保存后，将在这里保留可恢复的备份。").foregroundStyle(.secondary) }
                }
                Section("查找可恢复文字") {
                    TextField("搜索当前文稿、废纸篓和历史", text: $query)
                    ForEach(recoveryResults) { entry in
                        Button { recovery = entry } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.title.isEmpty ? "无标题文稿" : entry.title)
                                Text(entry.source).font(.caption).foregroundStyle(.secondary)
                                Text(entry.body.prefix(120)).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }.buttonStyle(.plain)
                    }
                    if !query.isEmpty && recoveryResults.isEmpty { Text("没有找到匹配的文字。").foregroundStyle(.secondary) }
                }
                if let message { Section { Text(message).foregroundStyle(.secondary) } }
            }.navigationTitle("备份与恢复")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
                .fileImporter(isPresented: $importing, allowedContentTypes: [.jianzuoBackup, .json, .data]) { result in
                    do { load(try result.get()) } catch { message = error.localizedDescription }
                }
                .fileExporter(isPresented: $exporting, document: BackupFileDocument(data: exportData), contentType: .jianzuoBackup, defaultFilename: "简作备份-\(WritingDay.key()).jzb") { result in
                    if case .failure(let error) = result { message = error.localizedDescription }
                }
                .confirmationDialog("合并这份备份？文稿将作为副本加入，现有内容会保留。", isPresented: $confirmMerge, titleVisibility: .visible) {
                    Button("合并恢复") {
                        do {
                            if let preview { try library.mergeBackup(preview, includeStatistics: includeStatistics); message = "已恢复 \(preview.documents.count) 篇文稿（含废纸篓）。" }
                            preview = nil
                        } catch { message = error.localizedDescription }
                    }
                }
                .sheet(item: $recovery) { entry in
                    NavigationStack {
                        ScrollView { Text(entry.body).frame(maxWidth: .infinity, alignment: .leading).padding().textSelection(.enabled) }
                            .navigationTitle(entry.title.isEmpty ? "恢复预览" : entry.title)
                            .toolbar {
                                ToolbarItem(placement: .cancellationAction) { Button("取消") { recovery = nil } }
                                ToolbarItem(placement: .confirmationAction) { Button("保存为新文稿") {
                                    library.createDocument(title: entry.title + " · 恢复", body: entry.body, notebook: entry.notebook)
                                    library.saveNow(); recovery = nil
                                } }
                            }
                    }.featureSheetSize()
                }
        }.featureSheetSize()
    }
    private func load(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do { let decoded = try BackupCodec.decode(Data(contentsOf: url)); preview = decoded; previewName = url.lastPathComponent; includeStatistics = false; message = nil }
        catch { preview = nil; message = error.localizedDescription }
    }
    private var recoveryResults: [RecoveryEntry] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return [] }
        var result: [RecoveryEntry] = []
        for document in library.documents {
            if document.title.localizedStandardContains(term) || document.body.localizedStandardContains(term) {
                result.append(RecoveryEntry(id: document.id.uuidString, title: document.title, body: document.body, notebook: document.notebook, source: document.deletedAt == nil ? "当前文稿" : "废纸篓"))
            }
            for revision in document.history where revision.title.localizedStandardContains(term) || revision.body.localizedStandardContains(term) {
                result.append(RecoveryEntry(id: document.id.uuidString + revision.id.uuidString, title: revision.title, body: revision.body, notebook: document.notebook, source: "历史 · " + revision.createdAt.formatted(date: .abbreviated, time: .shortened)))
                if result.count >= 200 { return result }
            }
            if result.count >= 200 { break }
        }
        return result
    }
}

private struct RecoveryEntry: Identifiable {
    var id: String
    var title: String
    var body: String
    var notebook: Notebook
    var source: String
}
