#!/usr/bin/env python3
"""Diagnose genuinely free access to Microsoft's TRELLIS.2 ZeroGPU Space.

No paid tokens, no proprietary photos, no model generation during probe.
"""
import json
import os
import sys
import traceback
from pathlib import Path
from gradio_client import Client

out=Path("trellis-test-result")
out.mkdir(exist_ok=True)
try:
    c=Client("microsoft/TRELLIS.2",verbose=False,download_files=str(out))
    info=c.view_api(return_format="dict",print_info=False)
    Path(out/"api.json").write_text(json.dumps(info,indent=2,default=str)[:80000])
    names=list(info.get("named_endpoints",{}).keys())
    unnamed=list(info.get("unnamed_endpoints",{}).keys())
    print("Space API reachable. Named endpoints:",names)
    print("Unnamed endpoints:",unnamed)
    for name,ep in info.get("named_endpoints",{}).items():
        if any(t in name.lower() for t in ("preprocess","image","extract","predict")):
            print("Endpoint",name,"parameters:",str(ep)[:2500])
    print("SUCCESS: public TRELLIS.2 API can be contacted from GitHub Actions.")
except Exception as e:
    print("FAILURE:",type(e).__name__,str(e)[:1500])
    traceback.print_exc()
    Path(out/"error.txt").write_text(type(e).__name__+": "+str(e))
    sys.exit(1)
