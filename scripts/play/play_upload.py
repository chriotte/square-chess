"""Uploads a signed AAB to a Play track.

Usage: play_upload.py <bundle.aab> <track[:status][,track[:status]...]> "<release notes>"
Status defaults to completed. Until the app's first public release, Play only
accepts production releases as draft (e.g. internal,production:draft); the owner
then starts that rollout in Play Console.
The service-account key is read from ~/SquareChessSigning/play-service-account.json
(or SQUARECHESS_PLAY_KEY) and is never stored in this repository.
"""
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.dataespresso.squarechess"
KEY = os.environ.get("SQUARECHESS_PLAY_KEY", str(Path.home() / "SquareChessSigning" / "play-service-account.json"))

bundle, track, notes = sys.argv[1], sys.argv[2], sys.argv[3]
creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
try:
    uploaded = edits.bundles().upload(packageName=PACKAGE, editId=eid,
        media_body=MediaFileUpload(bundle, mimetype="application/octet-stream", resumable=True)).execute()
    code = str(uploaded["versionCode"])
    print("uploaded version code", code)
    for spec in track.split(","):
        name, _, status = spec.partition(":")
        edits.tracks().update(packageName=PACKAGE, editId=eid, track=name, body={
            "track": name,
            "releases": [{"versionCodes": [code], "status": status or "completed",
                          "releaseNotes": [{"language": "en-GB", "text": notes}]}],
        }).execute()
    edits.validate(packageName=PACKAGE, editId=eid).execute()
    print("committed", edits.commit(packageName=PACKAGE, editId=eid).execute()["id"], "to", track)
except Exception:
    edits.delete(packageName=PACKAGE, editId=eid).execute()
    raise
