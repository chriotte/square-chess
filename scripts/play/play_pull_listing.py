"""Copies the Play Console listing text (en-GB) into fastlane/metadata/android/en-US.

Use after editing the listing in Play Console, so that F-Droid and the next
play_listing.py upload show the same text. Read-only on the Play side.
"""
import os
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE = "com.dataespresso.squarechess"
LANG = "en-GB"
KEY = os.environ.get("SQUARECHESS_PLAY_KEY", str(Path.home() / "SquareChessSigning" / "play-service-account.json"))
META = Path(__file__).resolve().parents[2] / "fastlane" / "metadata" / "android" / "en-US"

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
try:
    listing = edits.listings().get(packageName=PACKAGE, editId=eid, language=LANG).execute()
finally:
    edits.delete(packageName=PACKAGE, editId=eid).execute()

for field, name in (("title", "title.txt"), ("shortDescription", "short_description.txt"), ("fullDescription", "full_description.txt")):
    text = listing.get(field, "").replace("\r\n", "\n").strip() + "\n"
    path = META / name
    changed = not path.exists() or path.read_text(encoding="utf-8") != text
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"{name}: {'updated' if changed else 'unchanged'} ({len(text.strip())} characters)")
