"""Uploads the Square Chess store listing to Google Play.

The text and images come from fastlane/metadata/android/en-US, the same files
F-Droid reads, so both stores always show the same listing. Play uses en-GB.
The service-account key is read from ~/SquareChessSigning/play-service-account.json
(or SQUARECHESS_PLAY_KEY) and is never stored in this repository.
"""
import os
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.dataespresso.squarechess"
LANG = "en-GB"
KEY = os.environ.get("SQUARECHESS_PLAY_KEY", str(Path.home() / "SquareChessSigning" / "play-service-account.json"))
META = Path(__file__).resolve().parents[2] / "fastlane" / "metadata" / "android" / "en-US"

def text(name):
    return (META / name).read_text(encoding="utf-8").strip()

TITLE, SHORT, FULL = text("title.txt"), text("short_description.txt"), text("full_description.txt")
assert len(TITLE) <= 30 and len(SHORT) <= 80 and len(FULL) <= 4000, (len(TITLE), len(SHORT), len(FULL))
IMAGES = META / "images"

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
try:
    edits.details().update(packageName=PACKAGE, editId=eid, body={
        "defaultLanguage": LANG,
        "contactEmail": "support@dataespresso.com",
        "contactWebsite": "https://dataespresso.com/",
    }).execute()
    edits.listings().update(packageName=PACKAGE, editId=eid, language=LANG, body={
        "language": LANG, "title": TITLE, "shortDescription": SHORT, "fullDescription": FULL,
    }).execute()
    images = edits.images()
    def replace(kind, files):
        images.deleteall(packageName=PACKAGE, editId=eid, language=LANG, imageType=kind).execute()
        for f in files:
            images.upload(packageName=PACKAGE, editId=eid, language=LANG, imageType=kind,
                          media_body=MediaFileUpload(str(f), mimetype="image/png")).execute()
        print(kind, len(files))
    replace("icon", [IMAGES / "icon.png"])
    replace("featureGraphic", [IMAGES / "featureGraphic.png"])
    replace("phoneScreenshots", sorted((IMAGES / "phoneScreenshots").glob("*.png"), key=lambda p: int(p.stem)))
    edits.validate(packageName=PACKAGE, editId=eid).execute()
    print("committed edit", edits.commit(packageName=PACKAGE, editId=eid).execute().get("id"))
except HttpError as e:
    print("FAILED:", e.status_code, e.content.decode()[:1500])
    edits.delete(packageName=PACKAGE, editId=eid).execute()
    sys.exit(1)
