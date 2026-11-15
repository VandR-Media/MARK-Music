#!/usr/bin/env python3
"""Actually generate a free TRELLIS.2 GLB from OWNER-APPROVED MARK Cosmic art.

Runs only on an experimental branch, never with paid tokens. Inputs are a
small base64 encoded JPEG of the approved FICTIONAL character concept, not
the private source photographs. Reads 100%-local file from repository.
"""
import base64
import json
import os
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
result={"status":"starting","service":"microsoft/TRELLIS.2",
        "paid":False,"input":"user-approved generated character concept"}
def report(status,extra=None):
    result["status"]=status
    if extra: result.update(extra)
    (ROOT/"result.json").write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str))
    print(json.dumps(result,ensure_ascii=False,default=str),flush=True)

try:
    if not SOURCE.is_file():
        raise RuntimeError("Approved character input was not committed")
    image.write_bytes(base64.b64decode(SOURCE.read_text().strip(),validate=True))
    print("Source approved MARK Cosmic image:",image.stat().st_size,"bytes",flush=True)
    start=time.monotonic()
    client=Client("microsoft/TRELLIS.2",verbose=False,
                  download_files=str(ROOT))
    report("connected")
    # The free Space has a .load() handler creating a temporary session dir.
    try:
        client.predict(api_name="/start_session")
        print("Session started",flush=True)
    except Exception as e:
        print("Session start note:",type(e).__name__,str(e)[:300],flush=True)
    try:
        processed=client.predict(handle_file(str(image)),api_name="/preprocess_image")
        print("Preprocessing return:",str(processed)[:400],flush=True)
        if isinstance(processed,(list,tuple)):
            processed=processed[0]
        if isinstance(processed,dict):
            processed=processed.get("path") or processed.get("url")
        if processed and Path(str(processed)).exists():
            image_to_use=str(processed)
        else:
            image_to_use=str(image)
        report("preprocessed")
    except Exception as e:
        print("Preprocess failed, trying original image:",type(e).__name__,str(e)[:500],flush=True)
        image_to_use=str(image)
        report("preprocess_unavailable")

    # 512-resolution / short sampler / low-res GLB stay within anonymous
    # Hugging Face ZeroGPU allowance; no premium plans or API keys.
    print("Submitting 512-resolution image-to-3D inference...",flush=True)
    prediction=client.predict(
        handle_file(image_to_use), 2026,"512",
        7.5,.7,12,5.0, 7.5,.5,12,3.0, 1.0,0.0,12,3.0,
        api_name="/image_to_3d")
    print("3D inference finished, preview result:",str(prediction)[:300],flush=True)
    report("inference_success",{"generation_seconds":round(time.monotonic()-start,1)})

    glb=client.predict(100000,1024,api_name="/extract_glb")
    print("GLB extraction output:",str(glb)[:700],flush=True)
    candidates=[]
    if isinstance(glb,(list,tuple)):
        candidates+=list(glb)
    else:candidates.append(glb)
    for item in candidates:
        if isinstance(item,dict):item=item.get("path") or item.get("url")
        if isinstance(item,str) and Path(item).is_file() and Path(item).suffix.lower()==".glb":
            dst=ROOT/"MARK_Cosmic_TRELLIS2_512_test.glb"
            if Path(item).resolve()!=dst.resolve():shutil.copyfile(item,dst)
            if dst.stat().st_size<1000:raise RuntimeError("Extracted GLB unusually small")
            report("glb_generated",{"glb_size":dst.stat().st_size,
                    "elapsed_seconds":round(time.monotonic()-start,1)})
            sys.exit(0)
    raise RuntimeError("Model did not return a downloadable GLB file")
except Exception as e:
    report("failed",{"error_type":type(e).__name__,"error":str(e)[:900]})
    traceback.print_exc()
    # Fail honestly: the experiment did not produce a usable model.
    sys.exit(1)
