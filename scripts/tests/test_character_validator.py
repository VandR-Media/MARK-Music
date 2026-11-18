"""Regression checks for the production MARK Cosmic GLB asset gate."""
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "character"))
from validate_character import GlbError, analyze, parse_glb, _accessor_elements


def make_glb(document, blob=b"\0\0\0\0"):
    packed = json.dumps(document, separators=(",", ":")).encode()
    packed += b" " * (-len(packed) % 4)
    blob += b"\0" * (-len(blob) % 4)
    return (b"glTF" + struct.pack("<II", 2, 12+8+len(packed)+8+len(blob)) +
            struct.pack("<II", len(packed), 0x4e4f534a) + packed +
            struct.pack("<II", len(blob), 0x004e4942) + blob)


class CharacterValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "character.glb"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, doc, binary=b"\0\0\0\0"):
        self.path.write_bytes(make_glb(doc, binary))

    def minimal(self):
        return {"asset": {"version": "2.0"},
                "buffers": [{"byteLength": 4}],
                "nodes": [], "skins": [], "meshes": []}

    def test_can_parse_valid_glb_envelope(self):
        self.write(self.minimal())
        doc, binary, n = parse_glb(self.path)
        self.assertEqual(doc["asset"]["version"], "2.0")
        self.assertEqual(binary, b"\0\0\0\0")
        self.assertEqual(n, self.path.stat().st_size)

    def test_rejects_truncated_or_fake_binary(self):
        self.path.write_bytes(b"PNG")
        with self.assertRaises(GlbError):
            parse_glb(self.path)
        self.write(self.minimal())
        with self.path.open("ab") as output:
            output.write(b"damage")
        with self.assertRaises(GlbError):
            parse_glb(self.path)

    def test_rejects_external_uri_model(self):
        doc = self.minimal()
        doc["buffers"][0]["uri"] = "https://example.invalid/model.bin"
        self.write(doc)
        report = analyze(self.path)
        self.assertFalse(report["ok"])
        self.assertIn("External", " ".join(report["issues"]))

    def test_rejects_empty_mesh_without_materials_skin_face_rig_or_clips(self):
        self.write(self.minimal())
        report = analyze(self.path)
        self.assertFalse(report["ok"])
        self.assertEqual(report["metrics"]["joints"], 0)
        for required in ("humanoid", "Skinned", "facial blend", "texture",
                         "Idle_Breathe"):
            self.assertIn(required.lower(), " ".join(report["issues"]).lower())

    def test_rejects_22_bone_rigid_skin_which_cannot_deform_naturally(self):
        doc = self.minimal()
        doc["nodes"] = ([{"name": "Bone" + str(i)} for i in range(22)] +
                        [{"name": "Mark", "skin": 0, "mesh": 0}])
        doc["skins"] = [{"joints": list(range(22)), "inverseBindMatrices": 0}]
        doc["bufferViews"] = [{"buffer": 0, "byteOffset": 0, "byteLength": 16}]
        doc["accessors"] = [{"bufferView": 0, "componentType": 5126,
                             "count": 1, "type": "VEC4"}]
        doc["meshes"] = [{
            "primitives": [{"attributes": {
                "POSITION": 0, "WEIGHTS_0": 0, "JOINTS_0": 0,
                "NORMAL": 0, "TEXCOORD_0": 0}}]}]
        self.write(doc, struct.pack("<4f", 1.0, 0.0, 0.0, 0.0))
        report = analyze(self.path)
        self.assertFalse(report["ok"])
        self.assertTrue(any("50-110" in i for i in report["issues"]))
        self.assertTrue(any("rigid" in i for i in report["issues"]))
        self.assertEqual(report["metrics"]["soft_skinning_fraction"], 0)

    def test_read_interleaved_normalized_joint_weights(self):
        doc = self.minimal()
        doc["bufferViews"] = [{"buffer": 0, "byteOffset": 0,
                               "byteLength": 16, "byteStride": 8}]
        doc["accessors"] = [{"bufferView": 0, "componentType": 5121,
                             "count": 2, "type": "VEC4", "normalized": True}]
        raw = bytes([153, 102, 0, 0, 0, 0, 0, 0, 204, 51, 0, 0, 0, 0, 0, 0])
        doc["buffers"][0]["byteLength"] = len(raw)
        self.write(doc, raw)
        doc2, data, _ = parse_glb(self.path)
        values, count = _accessor_elements(doc2, data, 0)
        self.assertEqual(count, 2)
        self.assertAlmostEqual(values[0][0], .6, places=3)
        self.assertAlmostEqual(values[0][1], .4, places=3)
        self.assertAlmostEqual(values[1][0], .8, places=3)

    def test_declares_visual_art_review_separate_from_technical_gate(self):
        self.write(self.minimal())
        report = analyze(self.path)
        self.assertTrue(any("facial likeness" in w for w in report["warnings"]))


if __name__ == "__main__":
    unittest.main()
