#!/usr/bin/env python3
"""Fetch the pinned, checksummed llama.cpp source; never fetch a floating branch."""
import hashlib,os,pathlib,shutil,tarfile,tempfile,urllib.request
root=pathlib.Path(__file__).resolve().parent.parent
revision="5193ef1d4f7c95b006ab3b9f857ed27be9a63235"
sha256="b5af8224f044d5cf45c11be048141551104139ead7069ddc616d347e0ca12d1c"
destination=root/"apps/companion-android/build/llama-source"
if (destination/".nexus-revision").is_file() and (destination/".nexus-revision").read_text()==revision:
 print("Pinned llama.cpp already prepared");raise SystemExit(0)
with tempfile.TemporaryDirectory() as folder:
 archive=pathlib.Path(os.environ.get("NEXUS_LLAMA_ARCHIVE",str(pathlib.Path(folder)/"llama.tar.gz")))
 if not archive.exists():
  urllib.request.urlretrieve(f"https://codeload.github.com/ggml-org/llama.cpp/tar.gz/{revision}",archive)
 with archive.open("rb") as stream: assert hashlib.file_digest(stream,"sha256").hexdigest()==sha256,"llama.cpp archive checksum mismatch"
 with tarfile.open(archive) as source:source.extractall(folder,filter="data")
 destination.parent.mkdir(parents=True,exist_ok=True)
 if destination.exists():shutil.rmtree(destination)
 shutil.move(str(pathlib.Path(folder)/("llama.cpp-"+revision)),destination)
 (destination/".nexus-revision").write_text(revision)
print("Pinned llama.cpp prepared:",revision)
