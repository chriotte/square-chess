"""Read-only: prints the current store listing text with visible control characters."""
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE = "com.dataespresso.squarechess"
KEY = str(Path.home() / "SquareChessSigning" / "play-service-account.json")
creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
try:
    for listing in edits.listings().list(packageName=PACKAGE, editId=eid).execute().get("listings", []):
        for field in ("title", "shortDescription", "fullDescription"):
            print(f"== {listing['language']} {field} ({len(listing.get(field, ''))} chars)")
            print(repr(listing.get(field)) if "--repr" in sys.argv else listing.get(field))
finally:
    edits.delete(packageName=PACKAGE, editId=eid).execute()
