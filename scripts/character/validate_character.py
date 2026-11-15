#!/usr/bin/env python3
"""
Offline validation gate for an ARTIST-AUTHORED MARK Cosmic glTF 2.0 / GLB.

The validator cannot judge whether the character looks like Mark. That visual
approval must be performed against the owner-approved character turnaround.

Usage:
    python3 scripts/character/validate_character.py <file.glb>
    python3 scripts/character/validate_character.py <file.glb> --json

Requires only Python's standard library; no GitHub credentials or GMS.
"""
import argparse
import json
import math
import struct
import sys
from pathlib import Path

COMPONENT_FORMATS = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
COMPONENT_BYTES = {k: struct.calcsize("<" + v) for k, v in COMPONENT_FORMATS.items()}
TYPE_COUNTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
REQUIRED_NPC_CLIPS = {"Idle_Breathe", "NPC_Wave"}
REQUIRED_FACE_SHAPES = {"BlinkLeft", "BlinkRight", "Smile", "MouthOpen"}
MAX_MODEL_BYTES = 35 * 1024 * 1024


class GlbError(ValueError):
    pass


def parse_glb(path):
    """Read strict single-file GLB, rejecting external buffers and broken chunks."""
    data = Path(path).read_bytes()
    if len(data) < 20 or data[:4] != b"glTF":
        raise GlbError("Not a glTF 2.0 binary (.glb)")
    version, total = struct.unpack_from("<II", data, 4)
    if version != 2 or total != len(data):
        raise GlbError("GLB version/declared length mismatch")
    chunks = []
    offset = 12
    while offset < len(data):
        if offset + 8 > len(data):
            raise GlbError("Truncated GLB chunk header")
        length, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        if length % 4 != 0 or offset + length > len(data):
            raise GlbError("Unaligned or truncated GLB chunk")
        chunks.append((kind, data[offset:offset + length]))
        offset += length
    if not chunks or chunks[0][0] != 0x4E4F534A:
        raise GlbError("First GLB chunk must be JSON")
    try:
        document = json.loads(chunks[0][1].decode("utf-8").rstrip(" \x00"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GlbError("Unreadable glTF JSON: " + str(exc)) from exc
    if document.get("asset", {}).get("version") != "2.0":
        raise GlbError("Only glTF 2.0 assets are supported")
    if len(document.get("buffers", [])) != 1:
        raise GlbError("Expect one embedded binary buffer")
    buffer = document["buffers"][0]
    if "uri" in buffer:
        raise GlbError("External or data-URI buffers are not permitted")
    bins = [chunk for kind, chunk in chunks[1:] if kind == 0x004E4942]
    if len(bins) != 1 or buffer.get("byteLength", -1) > len(bins[0]):
        raise GlbError("Missing or truncated embedded BIN chunk")
    return document, bins[0], len(data)


def _accessor_elements(doc, binary, index, max_items=None):
    """Read an accessor including byte offsets / interleaved strides."""
    accessors = doc.get("accessors", [])
    views = doc.get("bufferViews", [])
    if not isinstance(index, int) or index < 0 or index >= len(accessors):
        raise GlbError("Invalid accessor index: " + str(index))
    a = accessors[index]
    if "sparse" in a:
        raise GlbError("Sparse WEIGHTS accessors require preprocessing")
    idx = a.get("bufferView")
    if idx is None or not isinstance(idx, int) or idx < 0 or idx >= len(views):
        raise GlbError("Accessor bufferView missing")
    view = views[idx]
    component = a.get("componentType")
    kind = a.get("type")
    if component not in COMPONENT_FORMATS or kind not in TYPE_COUNTS:
        raise GlbError("Unsupported accessor format")
    count = a.get("count", -1)
    if not isinstance(count, int) or count < 0:
        raise GlbError("Accessor count invalid")
    width = TYPE_COUNTS[kind]
    stride = view.get("byteStride", COMPONENT_BYTES[component] * width)
    size = COMPONENT_BYTES[component] * width
    if stride < size:
        raise GlbError("Accessor stride is too short")
    start = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    bound = view.get("byteOffset", 0) + view.get("byteLength", 0)
    if start < 0 or bound > len(binary):
        raise GlbError("Accessor buffer lies outside BIN chunk")
    fmt = "<" + COMPONENT_FORMATS[component] * width
    values = []
    for i in range(min(count, max_items or count)):
        pos = start + i * stride
        if pos + size > bound:
            raise GlbError("Accessor data outside bufferView")
        row = struct.unpack_from(fmt, binary, pos)
        if a.get("normalized", False) and component in (5121, 5123):
            denom = 255.0 if component == 5121 else 65535.0
            row = tuple(x / denom for x in row)
        values.append(row)
    return values, count


def analyze(path):
    issues = []
    warnings = []
    metrics = {}
    try:
        doc, binary, size = parse_glb(path)
    except (OSError, GlbError) as exc:
        return {"ok": False, "issues": [str(exc)], "warnings": [], "metrics": {}}

    metrics["bytes"] = size
    if size > MAX_MODEL_BYTES:
        issues.append("Package exceeds 35 MiB mobile asset budget")

    skins = doc.get("skins", [])
    nodes = doc.get("nodes", [])
    meshes = doc.get("meshes", [])
    images = doc.get("images", [])
    textures = doc.get("textures", [])
    materials = doc.get("materials", [])
    animations = doc.get("animations", [])

    if len(skins) != 1:
        issues.append("Expected exactly one shared humanoid skin")
    joints = skins[0].get("joints", []) if skins else []
    metrics["joints"] = len(joints)
    if len(joints) < 50 or len(joints) > 110:
        issues.append("Humanoid requires 50-110 exported deformation bones")
    if any(not isinstance(x, int) or x < 0 or x >= len(nodes) for x in joints):
        issues.append("Skin references non-existent nodes")
    if skins and "inverseBindMatrices" not in skins[0]:
        issues.append("Missing inverse bind matrices")
    if not any("skin" in node for node in nodes):
        issues.append("No skinned character instance is present in the scene")

    triangle_count = 0
    vertex_count = 0
    skinned_primitives = []
    face_targets = set()
    for mesh in meshes:
        names = mesh.get("extras", {}).get("targetNames", [])
        face_targets.update(str(x) for x in names)
        for primitive in mesh.get("primitives", []):
            attributes = primitive.get("attributes", {})
            pidx = attributes.get("POSITION")
            if pidx is None:
                issues.append("Mesh primitive has no POSITION")
                continue
            try:
                pos_count = doc["accessors"][pidx]["count"]
                indices = primitive.get("indices")
                vertex_count += pos_count
                n = doc["accessors"][indices]["count"] if indices is not None else pos_count
                if primitive.get("mode", 4) != 4:
                    issues.append("Only indexed triangle mesh primitives are approved")
                triangle_count += n // 3
            except (KeyError, IndexError, TypeError):
                issues.append("Invalid primitive POSITION or indices accessor")
                continue
            skin_attrs = {"JOINTS_0", "WEIGHTS_0"}
            if skin_attrs.issubset(attributes):
                skinned_primitives.append((attributes, pos_count))
                if "TEXCOORD_0" not in attributes or "NORMAL" not in attributes:
                    issues.append("Skinned surface needs UV0 and vertex normals")
            if primitive.get("targets"):
                if len(primitive["targets"]) < 4:
                    warnings.append("One facial mesh has fewer than four morph targets")

    metrics.update(vertices=vertex_count, triangles=triangle_count,
                   skinned_primitives=len(skinned_primitives),
                   morph_names=sorted(face_targets))
    if not 15000 <= triangle_count <= 75000:
        issues.append("Character mesh should have 15k-75k triangles (LOD0)")
    if not skinned_primitives:
        issues.append("No skinned mesh with JOINTS_0 and WEIGHTS_0")
    missing_faces = REQUIRED_FACE_SHAPES - face_targets
    if missing_faces:
        issues.append("Missing named facial blend shapes: " + ", ".join(sorted(missing_faces)))

    # Detect the old all-rigid 1.0 weight assignment. This measures REAL
    # vertex skin weights and cannot be passed by merely renaming bones.
    soft_vertices = 0
    total_vertices = 0
    for attributes, count in skinned_primitives:
        idx = attributes["WEIGHTS_0"]
        try:
            rows, _ = _accessor_elements(doc, binary, idx)
        except (GlbError, IndexError, KeyError) as exc:
            issues.append("Unreadable skin weights: " + str(exc))
            continue
        if len(rows) != count:
            issues.append("Skin weight count differs from vertex count")
        for row in rows:
            if any(not math.isfinite(x) for x in row):
                issues.append("Skin contains non-finite weights")
                break
            total_vertices += 1
            if sum(1 for x in row if x > 0.08) >= 2:
                soft_vertices += 1
    metrics["soft_skinning_fraction"] = round(
        soft_vertices / total_vertices, 4) if total_vertices else 0.0
    if metrics["soft_skinning_fraction"] < 0.08:
        issues.append("Too few softly weighted joints; rigid primitive skinning rejected")

    # Textures must be physically included inside the GLB; no HTTP loads.
    for img in images:
        if "uri" in img:
            issues.append("External/image URI not allowed: image must be embedded")
        if "bufferView" not in img:
            issues.append("Embedded image is missing its bufferView")
    metrics["embedded_images"] = len(images)
    if len(images) < 3 or len(textures) < 3:
        issues.append("Missing authored UV/PBR texture set (at least 3 images/textures)")
    with_basecolor = sum(
        1 for mat in materials
        if "baseColorTexture" in mat.get("pbrMetallicRoughness", {})
    )
    with_normals = sum(1 for mat in materials if "normalTexture" in mat)
    metrics["textured_materials"] = with_basecolor
    metrics["normal_mapped_materials"] = with_normals
    if with_basecolor < 2 or with_normals < 1:
        issues.append("Face and clothing must use PBR base-color and normal maps")

    anim_names = {str(a.get("name", "")) for a in animations}
    metrics["animation_names"] = sorted(anim_names)
    if not REQUIRED_NPC_CLIPS.issubset(anim_names):
        issues.append("Required test clips: Idle_Breathe and NPC_Wave")
    for anim in animations:
        if not anim.get("channels") or not anim.get("samplers"):
            issues.append("Animation has no joint or morph channels: " + anim.get("name", "?"))

    # Rejected/review-only filenames are never sufficient to determine quality.
    # Visual approval requires inspecting Blender renders from 3 angles.
    warnings.append("Automated checks do not verify facial likeness, art quality or foot sliding")
    return {"ok": not issues, "issues": list(dict.fromkeys(issues)),
            "warnings": list(dict.fromkeys(warnings)), "metrics": metrics}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("glb", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    report = analyze(args.glb)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print("MARK Cosmic character: " + ("PASS" if report["ok"] else "FAIL"))
        for key, value in report["metrics"].items():
            print("  %s: %s" % (key, value))
        for issue in report["issues"]:
            print("  ERROR: " + issue)
        for warning in report["warnings"]:
            print("  NOTE: " + warning)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
