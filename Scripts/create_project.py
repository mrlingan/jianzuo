"""Regenerate the dependency-free, shared macOS/iOS Xcode project."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
objects = {}

def uid(name):
    return hashlib.sha1(name.encode()).hexdigest()[:24].upper()

def obj(name, kind, fields):
    key = uid(name)
    objects[key] = f"\t\t{key} = {{ isa = {kind}; {fields} }};"
    return key

def quoted(value):
    return json.dumps(value, ensure_ascii=False)

def group(name, children, path=None):
    return obj(name, "PBXGroup", "children = (" + ",".join(children) + "); sourceTree = \"<group>\"; " + (f"path = {quoted(path)};" if path else ""))

def reference(path, kind):
    return obj(path, "PBXFileReference", f"lastKnownFileType = {kind}; path = {quoted(path)}; sourceTree = \"<group>\";")

def build_file(path, file):
    return obj("build:" + path, "PBXBuildFile", f"fileRef = {file};")

sources = []
groups = []
for folder in ("Models", "Views"):
    refs = []
    for path in sorted((root / "Jianzuo" / folder).glob("*.swift")):
        ref = reference(path.name, "sourcecode.swift")
        refs.append(ref)
        sources.append(build_file(folder + "/" + path.name, ref))
    groups.append(group(folder, refs, folder))
app_ref = reference("JianzuoApp.swift", "sourcecode.swift")
sources.append(build_file("app", app_ref))
asset = reference("Assets.xcassets", "folder.assetcatalog")
resources = [build_file("assets", asset)]
groups.append(group("Resources", [asset], "Resources"))
app_group = group("appgroup", [app_ref] + groups, "Jianzuo")
test_ref = reference("LibraryTests.swift", "sourcecode.swift")
test_group = group("testgroup", [test_ref], "JianzuoTests")
app_product = obj("app-product", "PBXFileReference", 'explicitFileType = wrapper.application; path = Jianzuo.app; sourceTree = BUILT_PRODUCTS_DIR;')
test_product = obj("test-product", "PBXFileReference", 'explicitFileType = wrapper.cfbundle; path = JianzuoTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
products = group("products", [app_product, test_product])
root_group = group("root", [app_group, test_group, products])

def phase(name, kind, files):
    return obj(name, kind, 'buildActionMask = 2147483647; files = (' + ','.join(files) + '); runOnlyForDeploymentPostprocessing = 0;')

app_phases = [phase("sources", "PBXSourcesBuildPhase", sources), phase("frameworks", "PBXFrameworksBuildPhase", []), phase("resources", "PBXResourcesBuildPhase", resources)]
test_phases = [phase("test-sources", "PBXSourcesBuildPhase", [build_file("tests", test_ref)]), phase("test-frameworks", "PBXFrameworksBuildPhase", [])]

def configuration(name, settings):
    text = " ".join(f"{quoted(key)} = {quoted(value)};" for key, value in settings.items())
    return obj(name, "XCBuildConfiguration", f"buildSettings = {{ {text} }}; name = {name.split(':')[-1]};")

def configurations(prefix, base):
    refs = []
    for variant in ("Debug", "Release"):
        settings = dict(base)
        settings.update({"SWIFT_OPTIMIZATION_LEVEL": "-Onone" if variant == "Debug" else "-O", "DEBUG_INFORMATION_FORMAT": "dwarf" if variant == "Debug" else "dwarf-with-dsym"})
        if variant == "Debug":
            settings.update({"ENABLE_TESTABILITY": "YES", "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG $(inherited)", "ONLY_ACTIVE_ARCH": "YES"})
        refs.append(configuration(prefix + ":" + variant, settings))
    return obj(prefix + "-configs", "XCConfigurationList", f"buildConfigurations = ({','.join(refs)}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;")

project_configs = configurations("project", {
    "CLANG_ENABLE_MODULES": "YES", "CLANG_ENABLE_OBJC_ARC": "YES", "GCC_C_LANGUAGE_STANDARD": "gnu17",
    "SWIFT_VERSION": "5.0", "MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "17.0",
    "SDKROOT": "auto", "SUPPORTED_PLATFORMS": "macosx iphoneos iphonesimulator",
    "CODE_SIGN_STYLE": "Automatic", "ENABLE_USER_SCRIPT_SANDBOXING": "YES",
})
app_configs = configurations("app", {
    "PRODUCT_NAME": "$(TARGET_NAME)", "PRODUCT_BUNDLE_IDENTIFIER": "com.jianzuo.writing",
    "GENERATE_INFOPLIST_FILE": "YES", "INFOPLIST_KEY_CFBundleDisplayName": "简作",
    "INFOPLIST_KEY_LSApplicationCategoryType": "public.app-category.productivity",
    "INFOPLIST_KEY_UIApplicationSceneManifest_Generation": "YES", "INFOPLIST_KEY_UILaunchScreen_Generation": "YES",
    "INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone": "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight",
    "INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad": "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight",
    "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon", "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
    "TARGETED_DEVICE_FAMILY": "1,2", "MARKETING_VERSION": "1.0", "CURRENT_PROJECT_VERSION": "1",
    "ENABLE_APP_SANDBOX": "YES", "ENABLE_USER_SELECTED_FILES": "readwrite",
    "SWIFT_EMIT_LOC_STRINGS": "YES", "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @executable_path/Frameworks @executable_path/../Frameworks",
})
test_configs = configurations("test", {
    "PRODUCT_NAME": "$(TARGET_NAME)", "PRODUCT_BUNDLE_IDENTIFIER": "com.jianzuo.writing.tests",
    "GENERATE_INFOPLIST_FILE": "YES", "TARGETED_DEVICE_FAMILY": "1,2", "BUNDLE_LOADER": "$(TEST_HOST)",
    "TEST_HOST": "$(BUILT_PRODUCTS_DIR)/Jianzuo.app/Jianzuo",
    "TEST_HOST[sdk=macosx*]": "$(BUILT_PRODUCTS_DIR)/Jianzuo.app/Contents/MacOS/Jianzuo",
    "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @executable_path/Frameworks @loader_path/Frameworks",
})
app_target = obj("app-target", "PBXNativeTarget", f"buildConfigurationList = {app_configs}; buildPhases = ({','.join(app_phases)}); buildRules = (); dependencies = (); name = Jianzuo; productName = Jianzuo; productReference = {app_product}; productType = \"com.apple.product-type.application\";")
proxy = obj("test-proxy", "PBXContainerItemProxy", f"containerPortal = {uid('project')}; proxyType = 1; remoteGlobalIDString = {app_target}; remoteInfo = Jianzuo;")
dependency = obj("test-dependency", "PBXTargetDependency", f"target = {app_target}; targetProxy = {proxy};")
test_target = obj("test-target", "PBXNativeTarget", f"buildConfigurationList = {test_configs}; buildPhases = ({','.join(test_phases)}); buildRules = (); dependencies = ({dependency}); name = JianzuoTests; productName = JianzuoTests; productReference = {test_product}; productType = \"com.apple.product-type.bundle.unit-test\";")
project = obj("project", "PBXProject", f"attributes = {{ BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; TargetAttributes = {{ {app_target} = {{CreatedOnToolsVersion = 16.0;}}; {test_target} = {{CreatedOnToolsVersion = 16.0; TestTargetID = {app_target};}}; }}; }}; buildConfigurationList = {project_configs}; compatibilityVersion = \"Xcode 14.0\"; developmentRegion = zh-Hans; hasScannedForEncodings = 0; knownRegions = (en, Base, zh-Hans); mainGroup = {root_group}; productRefGroup = {products}; projectDirPath = \"\"; projectRoot = \"\"; targets = ({app_target},{test_target});")
(root / "Jianzuo.xcodeproj/project.pbxproj").write_text("// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n" + "\n".join(objects.values()) + f"\n}}; rootObject = {project}; }}\n")

def buildable(target, name, product):
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:Jianzuo.xcodeproj"/>'
app_b = buildable(app_target, "Jianzuo", "Jianzuo.app")
test_b = buildable(test_target, "JianzuoTests", "JianzuoTests.xctest")
scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{app_b}</BuildActionEntry></BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{test_b}</TestableReference></Testables></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_b}</BuildableProductRunnable></LaunchAction>
 <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_b}</BuildableProductRunnable></ProfileAction>
 <AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>'''
(root / "Jianzuo.xcodeproj/xcshareddata/xcschemes/Jianzuo.xcscheme").write_text(scheme)

assets = root / "Jianzuo/Resources/Assets.xcassets"
(assets / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}))
colors = {
    "Paper": [("0.980", "0.969", "0.945"), ("0.114", "0.110", "0.102")],
    "Sidebar": [("0.953", "0.937", "0.910"), ("0.145", "0.137", "0.125")],
    "Card": [("0.992", "0.984", "0.969"), ("0.129", "0.122", "0.114")],
    "AccentColor": [("0.690", "0.360", "0.240"), ("0.820", "0.520", "0.380")],
}
for name, variants in colors.items():
    folder = assets / (name + ".colorset")
    folder.mkdir(exist_ok=True)
    entries = []
    for index, (r, g, b) in enumerate(variants):
        entry = {"idiom": "universal", "color": {"color-space": "srgb", "components": {"red": r, "green": g, "blue": b, "alpha": "1.000"}}}
        if index: entry["appearances"] = [{"appearance": "luminosity", "value": "dark"}]
        entries.append(entry)
    (folder / "Contents.json").write_text(json.dumps({"colors": entries, "info": {"author": "xcode", "version": 1}}, indent=2))
