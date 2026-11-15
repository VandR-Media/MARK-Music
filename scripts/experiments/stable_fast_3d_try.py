#!/usr/bin/env python3
"""Try Stability AI Stable Fast 3D's public ZeroGPU demo with approved concept.

No paid API calls, no token. This is a tool verification, not release artwork.
"""
import base64
import json
import shutil
import sys
import time
import traceback
from pathlib import Path
from gradio_client import Client, handle_file

ROOT=Path("trellis-test-result")
ROOT.mkdir(exist_ok=True)
SOURCE=Path("scripts/experiments/mark_cosmic_front.jpg.b64")
image=ROOT/"mark_cosmic_approved_input.jpg"
status={"service":"stabilityai/stable-fast-3d","status":"starting","paid":False}
def report(s,**fields):
    status.update(status=s,**fields)
    (ROOT/"stable_fast_3d_status.json").write_text(json.dumps(status,indent=2,ensure_ascii=False,default=str))
    print(json.dumps(status,ensure_ascii=False,default=str),flush=True)
try:
    image.write_bytes(base64.b64decode(SOURCE.read_text(),validate=True))
    print("Source:",image.stat().st_size,flush=True)
    from PIL import Image
    Image.open(image).verify()
    report("source_ok")
    # The exact same free background-remover successfully used by TRELLIS.2.
    bria=Client("briaai/BRIA-RMBG-2.0",verbose=False,download_files=str(ROOT))
    rembg=bria.predict(handle_file(str(image)),api_name="/image")
    print("Background-removal result:",str(rembg)[:450],flush=True)
    candidate=rembg[0][0] if isinstance(rembg[0],(list,tuple)) else rembg[0]
    if isinstance(candidate,dict):candidate=candidate.get("path") or candidate.get("url")
    if isinstance(candidate,str) and Path(candidate).exists(): masked=candidate
    else:raise RuntimeError("No usable alpha-masked output from BRIA")
    from PIL import Image
    with Image.open(masked) as m:
        print("Alpha-masked input:",m.mode,m.size,flush=True)
        if m.mode!="RGBA":raise RuntimeError("BRIA output lacked alpha")
    report("masked")
    client=Client("stabilityai/stable-fast-3d",verbose=False,download_files=str(ROOT))
    schema=client.view_api(return_format="dict",print_info=False)
    names=list(schema.get("named_endpoints",{}))
    print("Stable Fast 3D free API endpoints:",names,flush=True)
    for n,e in schema.get("named_endpoints",{}).items():
        print("PARAMS",n,[(x.get("parameter_name"),x.get("parameter_default")) for x in e.get("parameters",[])],flush=True)
    report("connected",endpoints=names)
    req=next((x for x in names if "requires_bg_remove" in x),None)
    if req:
        initial=client.predict(handle_file(str(masked)),.85,api_name=req)
        print("Prepare background state:",str(initial)[:500],flush=True)
    else:
        print("No requires_bg_remove endpoint; attempting run directly",flush=True)
    run=next((x for x in names if "run_button" in x),None)
    if not run:
        raise RuntimeError("No public run_button Gradio endpoint")
    print("Starting anonymous one-shot 3D generation",flush=True)
    outputs=client.predict("Run",handle_file(str(masked)),.85,"None",-1,1024,api_name=run)
    print("Generator returned:",str(outputs)[:700],flush=True)
    items=outputs if isinstance(outputs,(tuple,list)) else [outputs]
    candidates=[]
    def scan(v):
        if isinstance(v,str) and v.lower().endswith(".glb"):candidates.append(v)
        elif isinstance(v,dict):
            for item in v.values():scan(item)
        elif isinstance(v,(tuple,list)):
            for item in v:scan(item)
    scan(items)
    for path in candidates:
        if Path(path).is_file() and Path(path).stat().st_size>1000:
            dest=ROOT/"MARK_Cosmic_StableFast3D_free_test.glb"
            if Path(path).resolve()!=dest.resolve():shutil.copyfile(path,dest)
            report("glb_generated",file_size=dest.stat().st_size)
            sys.exit(0)
    raise RuntimeError("No downloaded GLB in output")
except Exception as e:
    report("failed",error_type=type(e).__name__,error=str(e)[:1200])
    traceback.print_exc()
    sys.exit(1)
