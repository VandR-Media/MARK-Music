"""
Blender 4.x export helper for a REAL, artist-authored MARK Cosmic character.

Example:
  blender -b private_mark_cosmic.blend \
    --python scripts/character/export_character_blender.py \
    -- /tmp/MarkCosmic_Approved.glb

The original .blend file and reference photographs remain outside GitHub.
The export includes ONLY the MARK_COSMIC_EXPORT collection and its authored
armature, not camera refs, photographs or other private work.
"""
import os
import sys
import bpy


def fail(reason):
    raise RuntimeError("MARK Cosmic export: " + reason)


def export(out):
    collection = bpy.data.collections.get("MARK_COSMIC_EXPORT")
    if not collection:
        fail("Create MARK_COSMIC_EXPORT collection for the final character")

    objects = list(collection.all_objects)
    armatures = [obj for obj in objects if obj.type == "ARMATURE"]
    meshes = [obj for obj in objects if obj.type == "MESH"]
    if len(armatures) != 1:
        fail("Exactly one character armature is required")
    if len(meshes) < 3:
        fail("Expected authored head/body/clothing meshes (not primitive spheres)")

    rig = armatures[0]
    if len(rig.data.bones) < 50:
        fail("Humanoid rig is missing deformation joints")
    if not any(obj.find_armature() == rig for obj in meshes):
        fail("Meshes must be bound to the character armature")
    for mesh in meshes:
        if not mesh.data.uv_layers:
            fail("Missing UV map on " + mesh.name)
        if not mesh.data.materials:
            fail("Missing authored PBR material on " + mesh.name)
    if not any(mesh.data.shape_keys and
               {"BlinkLeft", "BlinkRight", "Smile", "MouthOpen"}.issubset(
                   {key.name for key in mesh.data.shape_keys.key_blocks})
               for mesh in meshes):
        fail("Face needs named morphs BlinkLeft/BlinkRight/Smile/MouthOpen")

    if not {"Idle_Breathe", "NPC_Wave"}.issubset(
            {a.name for a in bpy.data.actions}):
        fail("Need two authored test actions: Idle_Breathe and NPC_Wave")

    for obj in bpy.context.view_layer.objects:
        obj.select_set(False)
    for obj in objects:
        if obj.type in ("MESH", "ARMATURE"):
            obj.hide_set(False)
            obj.select_set(True)
    bpy.context.view_layer.objects.active = rig

    # Use the exporter options supported by the installed Blender version:
    # newer Blender releases renamed the animation selection flag.
    options = {
        "filepath": out,
        "export_format": "GLB",
        "use_selection": True,
        "export_texcoords": True,
        "export_normals": True,
        "export_tangents": True,
        "export_materials": "EXPORT",
        "export_skins": True,
        "export_morph": True,
        "export_morph_normal": True,
        "export_animations": True,
        "export_yup": True,
        "export_apply": False,
    }
    available = {prop.identifier for prop in
                 bpy.ops.export_scene.gltf.get_rna_type().properties}
    options = {key: value for key, value in options.items() if key in available}
    bpy.ops.export_scene.gltf(**options)
    if not os.path.isfile(out) or os.path.getsize(out) == 0:
        fail("GLB exporter did not create a file")
    print("Exported verified collection to", out)
    print("Next: python3 scripts/character/validate_character.py", out)
    print("Then visually approve FRONT/SIDE/BACK in a renderer, not an image mockup.")


if __name__ == "__main__":
    try:
        separator = sys.argv.index("--")
        output_path = os.path.abspath(sys.argv[separator + 1])
    except (ValueError, IndexError):
        fail("Usage: blender -b file.blend --python this_script.py -- /path/model.glb")
    export(output_path)
