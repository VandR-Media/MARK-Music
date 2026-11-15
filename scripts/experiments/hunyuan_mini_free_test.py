#!/usr/bin/env python3
"""Single anonymous and free 3D-generation attempt using Tencent's ZeroGPU demo.

Uses ONLY the approved fictional MARK Cosmic artwork, not any original photos.
Outputs local validated GLB when available; honest diagnostics otherwise.
"""
import base64, json, shutil, sys, time, traceback
from pathlib import Path
from gradio_client import Client, handle_file
from PIL import Image

root = Path("hunyuan-result")
root.mkdir(exist_ok=True)
source = Path("scripts/experiments/mark_cosmic_front.jpg.b64")
input_image = root / "mark_cosmic_approved_front.jpg"
status = {"service":"tencent/Hunyuan3D-2mini-Turbo","cost":"free ZeroGPU only","status":"started"}
def report(state, **kwargs):
    status.update(status=state, **kwargs)
    (root / "status.json").write_text(json.dumps(status,indent=2,ensure_ascii=False,default=str))
    print(json.dumps(status,ensure_ascii=False,default=str),flush=True)

def extract_files(obj):
    if isinstance(obj,str):
        if obj.lower().split("?")[0].endswith(".glb") or obj.lower().endswith(".obj"):
            yield obj
    elif isinstance(obj,dict):
        for a in obj.values():yield from extract_files(a)
    elif isinstance(obj,(list,tuple)):
        for x in obj:yield from extract_files(x)

try:
    input_image.write_bytes(base64.b64decode(source.read_text().strip(),validate=True))
    with Image.open(input_image) as im:
        print("Approved image:", im.size, im.mode, flush=True)
    client = Client("tencent/Hunyuan3D-2mini-Turbo",verbose=False,download_files=str(root))
    schema = client.view_api(return_format="dict", print_info=False)
    named = schema.get("named_endpoints",{})
    unnamed = schema.get("unnamed_endpoints",{})
    keys = list(named) + list(unnamed)
    print("Available API:",keys,flush=True)
    # Try the Gradio action that actually runs image -> 3D (not text2image).
    chosen = next((s for s in keys if "shape_generation" in s or "generation_all" in s),None)
    if chosen is None:
        chosen=next((s for s in keys if "generate" in s or "predict" in s),None)
    if chosen is None:
        raise RuntimeError("No public Hunyuan shape generation endpoint in Gradio schema")
    endpoint = named.get(chosen) or unnamed[chosen]
    params = endpoint["parameters"]
    print("Chosen endpoint",chosen,flush=True)
    print("Input names/defaults",[(p.get("parameter_name"),str(p.get("parameter_default"))[:80]) for p in params],flush=True)
    args = []
    image_used = False
    for i, p in enumerate(params):
        name = (p.get("parameter_name") or p.get("label") or "").lower().replace(" ","_")
        default = p.get("parameter_default")
        if name in ("image","image_prompt","input_image") or (
            not image_used and p.get("component")=="Image" and "mv_" not in name
        ):
            args.append(handle_file(str(input_image)))
            image_used = True
        elif "caption" in name or "text_prompt" in name:
            args.append(None)
        elif "mv_" in name or "multiview" in name:
            args.append(None)
        elif "step" in name:
            args.append(8)
        elif "resolution" in name or "octree" in name:
            args.append(192)
        elif "num_chunks" in name:
            args.append(8000)
        elif "seed" in name and "random" not in name:
            args.append(2026)
        elif "guidance" in name or "cfg" in name:
            args.append(5.0)
        elif "rembg" in name or "background" in name:
            args.append(True)
        elif "random" in name or "check_box" in name:
            args.append(False)
        elif default is not None:
            args.append(default)
        else:
            args.append(None)
    if not image_used:
        raise RuntimeError("No image prompt input matched; aborting instead of submitting text")
    report("submitted",endpoint=chosen)
    started=time.monotonic()
    if chosen in named:
        result=client.predict(*args,api_name=chosen)
    else:
        result=client.predict(*args,fn_index=int(chosen[1:]) if chosen.startswith("/") else int(chosen))
    print("Output shape:",str(result)[:1500],flush=True)
    report("returned",elapsed=round(time.monotonic()-started,1))
    choices=list(extract_files(result))
    print("Potential mesh paths",choices,flush=True)
    for c in choices:
        path=Path(c)
        if path.exists() and path.stat().st_size>1000:
            dest=root/"MARK_Cosmic_Hunyuan_Mesh.glb"
            if path.suffix.lower()==".glb":
                shutil.copy2(path,dest)
                raw=dest.read_bytes()
                if raw[:4]!=b"glTF":
                    raise RuntimeError("Downloaded file is not GLB 2.0")
                report("glb_generated",bytes=len(raw),file=str(dest))
                sys.exit(0)
    raise RuntimeError("Generator returned no downloadable GLB")
except Exception as e:
    report("failed",error_type=type(e).__name__,error=str(e)[:1400])
    traceback.print_exc()
    sys.exit(1)
