#!/usr/bin/env python3
"""One free anonymous Hunyuan mini Turbo by frogleo; approved concept only."""
import base64,json,traceback,sys,time,os
from pathlib import Path
from gradio_client import Client,handle_file
from PIL import Image
import requests
root=Path("frogleo-result");root.mkdir(exist_ok=True)
inp=root/"mark_cosmic_approved_front.jpg"
state={"service":"frogleo/Image-to-3D","free":True}
def report(s,**kwargs):
    state.update(status=s,**kwargs)
    (root/"result.json").write_text(json.dumps(state,indent=2,ensure_ascii=False))
    print(json.dumps(state,ensure_ascii=False),flush=True)
try:
    inp.write_bytes(base64.b64decode(Path("scripts/experiments/mark_cosmic_front.jpg.b64").read_text().strip(),validate=True))
    Image.open(inp).verify()
    client=Client("frogleo/Image-to-3D",verbose=False,download_files=str(root),hf_token=os.environ.get("HF_TOKEN") or None)
    api=client.view_api(return_format="dict",print_info=False)
    endpoints=list(api.get("named_endpoints",{}))
    print("Available functions:",endpoints,flush=True)
    ep=next((s for s in endpoints if "gen_shape" in s),None) or next((s for s in endpoints if "predict" in s or "generate" in s),None)
    if not ep:raise RuntimeError("No public shape generation endpoint")
    report("submitted",endpoint=ep)
    t=time.monotonic()
    # Exact public Gradio app inputs [image, num_steps, cfg, seed, octree_resolution, num_chunks, faces, randomize]
    output=client.predict(handle_file(str(inp)),5,5.5,2026,192,8000,10000,False,api_name=ep)
    print("Output:",str(output)[:1300],flush=True)
    items=output if isinstance(output,(tuple,list)) else [output]
    url=next((x for x in items if isinstance(x,str) and x.startswith("/static/") and x.endswith(".glb")),None)
    if not url:raise RuntimeError("Public model API did not return an accessible GLB URL")
    full="https://frogleo-image-to-3d.hf.space"+url
    req=requests.get(full,timeout=45)
    req.raise_for_status()
    dest=root/"MARK_Cosmic_HunyuanMini_white_mesh.glb"
    dest.write_bytes(req.content)
    if dest.stat().st_size<1024 or req.content[:4]!=b"glTF":
        raise RuntimeError("Returned file is not a valid GLB container")
    report("glb_generated",size=dest.stat().st_size,seconds=round(time.monotonic()-t,1),textured=False)
except Exception as ex:
    report("failed",error_type=type(ex).__name__,error=str(ex)[:1000])
    traceback.print_exc()
    sys.exit(1)
