"""Promotes an already uploaded version code to a Play track (no new upload).

Usage: play_promote.py <versionCode> <track> [rollout-fraction]
The release notes come from fastlane/metadata/android/en-US/changelogs/<versionCode>.txt.
A rollout fraction below 1 (for example 0.2) starts a staged rollout.
"""
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE = "com.dataespresso.squarechess"
KEY = os.environ.get("SQUARECHESS_PLAY_KEY", str(Path.home() / "SquareChessSigning" / "play-service-account.json"))
META = Path(__file__).resolve().parents[2] / "fastlane" / "metadata" / "android" / "en-US"

code, track = sys.argv[1], sys.argv[2]
fraction = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0
notes = (META / "changelogs" / f"{code}.txt").read_text(encoding="utf-8").strip()
release = {"versionCodes": [code], "releaseNotes": [{"language": "en-GB", "text": notes}]}
release.update({"status": "inProgress", "userFraction": fraction} if fraction < 1 else {"status": "completed"})

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
try:
    bundles = [str(b["versionCode"]) for b in edits.bundles().list(packageName=PACKAGE, editId=eid).execute().get("bundles", [])]
    if code not in bundles:
        raise SystemExit(f"Version code {code} is not uploaded (uploaded: {bundles})")
    edits.tracks().update(packageName=PACKAGE, editId=eid, track=track,
                          body={"track": track, "releases": [release]}).execute()
    edits.validate(packageName=PACKAGE, editId=eid).execute()
    print("committed", edits.commit(packageName=PACKAGE, editId=eid).execute()["id"], f"{code} -> {track} ({release['status']})")
except BaseException:
    edits.delete(packageName=PACKAGE, editId=eid).execute()
    raise
