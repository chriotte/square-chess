"""Read-only Play Console check: opens an edit, reads state, deletes the edit."""
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

KEY = os.environ.get("SQUARECHESS_PLAY_KEY", str(Path.home() / "SquareChessSigning" / "play-service-account.json"))
PACKAGE = sys.argv[1] if len(sys.argv) > 1 else "com.dataespresso.squarechess"

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
edits = api.edits()
try:
    edit = edits.insert(packageName=PACKAGE, body={}).execute()
except HttpError as e:
    print("OPEN EDIT FAILED:", e.status_code, e.reason)
    print(e.content.decode()[:800])
    sys.exit(1)
eid = edit["id"]
try:
    print("app found:", PACKAGE)
    details = edits.details().get(packageName=PACKAGE, editId=eid).execute()
    print("details:", details)
    listings = edits.listings().list(packageName=PACKAGE, editId=eid).execute()
    for l in listings.get("listings", []):
        print("listing:", l.get("language"), "|", l.get("title"), "|", l.get("shortDescription"))
    tracks = edits.tracks().list(packageName=PACKAGE, editId=eid).execute()
    for t in tracks.get("tracks", []):
        print("track:", t.get("track"), t.get("releases"))
    bundles = edits.bundles().list(packageName=PACKAGE, editId=eid).execute()
    print("bundles:", [b.get("versionCode") for b in bundles.get("bundles", [])])
finally:
    edits.delete(packageName=PACKAGE, editId=eid).execute()
    print("edit deleted (no changes)")
