#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
sketch_tool="${SKETCHTOOL:-/Applications/Sketch.app/Contents/MacOS/sketchtool}"
source_file="$project_root/Design/AppIcon/Jianzuo-Icon-Design.sketch"
exports="$project_root/Design/AppIcon/Exports"
export_ids="$(python3 - "$source_file" <<'PYIDS'
import json, sys, zipfile
names = {"AppIcon-iOS", "AppIcon-macOS", "Jianzuo-Icon-Design"}
found = {}
def visit(layer):
    if layer.get("name") in names:
        found[layer["name"]] = layer["do_objectID"]
    for child in layer.get("layers", []):
        visit(child)
with zipfile.ZipFile(sys.argv[1]) as document:
    for name in document.namelist():
        if name.startswith("pages/") and name.endswith(".json"):
            visit(json.loads(document.read(name)))
if names != found.keys():
    raise SystemExit("Missing named icon groups in Sketch source")
print(",".join(found.values()))
PYIDS
)"
"$sketch_tool" export layers "$source_file" --items="$export_ids" --output="$exports" --formats=png --trimmed=NO --overwriting=YES --group-contents-only=YES
swift "$project_root/Scripts/make_icon.swift" --normalize-masters
swift "$project_root/Scripts/make_icon.swift" --install
