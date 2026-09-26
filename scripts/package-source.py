#!/usr/bin/env python3
"""Pack tracked source, including every initialized submodule."""
import io
from pathlib import Path
import subprocess
import sys
import tarfile

root = Path(__file__).resolve().parents[1]
output = Path(sys.argv[1]).resolve()
version = sys.argv[2] if len(sys.argv) > 2 else "source"
paths = subprocess.check_output(
    ["git", "ls-files", "--recurse-submodules", "-z"], cwd=root
).decode().split("\0")
output.parent.mkdir(parents=True, exist_ok=True)
prefix = f"ArchDroid-{version}"
with tarfile.open(output, "w:gz") as archive:
    for name in sorted(filter(None, paths)):
        path = root / name
        if path.is_dir():
            raise SystemExit(f"Uninitialized submodule: {name}")
        archive.add(path, arcname=f"{prefix}/{name}", recursive=False)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root)
    info = tarfile.TarInfo(f"{prefix}/SOURCE_COMMIT")
    info.size = len(revision)
    archive.addfile(info, io.BytesIO(revision))
print(output)
