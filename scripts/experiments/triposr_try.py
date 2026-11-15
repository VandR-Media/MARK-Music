#!/usr/bin/env python3
"""One free anonymous StabilityAI TripoSR (GLB) experiment. No paid keys."""
import base64, json, traceback, shutil, sys, time
from pathlib import Path
from gradio_client import Client, handle_file
from PIL import Image
root=Path("tripo-result");root.mkdir(exist_ok=True)
src=Path("scripts/experiments/mark_cosmic_front.jpg.b64")
im=root/"approved_mark_cosmic.jpg"
s={"service":"stabilityai/TripoSR","free":True,"state":"start"}
def log(state,**kwargs):
    s.update(state=state,**kwargs)
    (root/"result.json").write_text(json.dumps(s,indent=2,ensure_ascii=False))
    print(json.dumps(s,ensure_ascii=False),flush=True)
try:
    im.write_bytes(base64.b64decode(src.read_text().strip(),validate=True))
    Image.open(im).verify()
    client=Client("stabilityai/TripoSR",verbose=False,download_files=str(root))
    schema=client.view_api(return_format="dict",print_info=False)
    ep=list(schema.get("named_endpoints",{}))
    print("TripoSR functions:",ep,flush=True)
    preprocess=next((n for n in ep if "preprocess" in n),None)
    generate=next((n for n in ep if "generate" in n),None)
    if not preprocess or not generate:raise RuntimeError("preprocess or generate not available")
    processed=client.predict(handle_file(str(im)),True,.85,api_name=preprocess)
    if isinstance(processed,(tuple,list)):processed=processed[0]
    if isinstance(processed,dict):processed=processed.get("path") or processed.get("url")
    print("Preprocessed",str(processed)[:250],flush=True)
    if not processed or not Path(processed).is_file():raise RuntimeError("No usable preprocessed image")
    log("preprocessed")
    started=time.monotonic()
    output=client.predict(handle_file(str(processed)),192,api_name=generate)
    print("Generated:",str(output)[:1000],flush=True)
    values=output if isinstance(output,(tuple,list)) else [output]
    for v in values:
        if isinstance(v,dict):v=v.get("path") or v.get("url")
        if isinstance(v,str) and v.lower().endswith(".glb") and Path(v).exists():
            dst=root/"MARK_Cosmic_TripoSR.glb"
            shutil.copy2(v,dst)
            if dst.read_bytes()[:4]!=b"glTF":raise RuntimeError("Invalid GLB")
            log("glb_generated",bytes=dst.stat().st_size,seconds=round(time.monotonic()-started,1))
            sys.exit(0)
    raise RuntimeError("Generator returned no GLB")
except Exception as exc:
    log("failed",error_type=type(exc).__name__,error=str(exc)[:1200])
    traceback.print_exc()
    sys.exit(1)
