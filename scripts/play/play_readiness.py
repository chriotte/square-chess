"""Read-only release readiness check for the parts the Play Developer API can see."""
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE = "com.dataespresso.squarechess"
KEY = str(Path.home() / "SquareChessSigning" / "play-service-account.json")
creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
edits = api.edits()
eid = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
ok = True
def check(label, passed, detail=""):
    global ok
    ok &= bool(passed)
    print(f"[{'OK' if passed else '!!'}] {label}{' - ' + detail if detail else ''}")
try:
    details = edits.details().get(packageName=PACKAGE, editId=eid).execute()
    check("Contact email", details.get("contactEmail"), details.get("contactEmail", "missing"))
    check("Default language", details.get("defaultLanguage"), details.get("defaultLanguage", "missing"))
    listings = edits.listings().list(packageName=PACKAGE, editId=eid).execute().get("listings", [])
    for l in listings:
        lang = l["language"]
        check(f"{lang} title", l.get("title"), l.get("title", ""))
        check(f"{lang} short description", l.get("shortDescription"), f"{len(l.get('shortDescription', ''))}/80")
        check(f"{lang} full description", l.get("fullDescription"), f"{len(l.get('fullDescription', ''))}/4000")
        for kind, minimum in (("icon", 1), ("featureGraphic", 1), ("phoneScreenshots", 2)):
            count = len(edits.images().list(packageName=PACKAGE, editId=eid, language=lang, imageType=kind)
                        .execute().get("images", []))
            check(f"{lang} {kind}", count >= minimum, f"{count} uploaded")
    bundles = [b["versionCode"] for b in edits.bundles().list(packageName=PACKAGE, editId=eid).execute().get("bundles", [])]
    print("     bundles:", bundles)
    for t in edits.tracks().list(packageName=PACKAGE, editId=eid).execute().get("tracks", []):
        for r in t.get("releases", []) or []:
            print(f"     track {t['track']}: {r.get('name')} codes={r.get('versionCodes')} status={r.get('status')}")
    prod = edits.tracks().get(packageName=PACKAGE, editId=eid, track="production").execute().get("releases", [])
    check("Production release prepared", prod, ", ".join(f"{r.get('name')} ({r.get('status')})" for r in prod) or "none")
    newest = max(bundles) if bundles else None
    check("Production uses newest bundle", prod and str(newest) in prod[0].get("versionCodes", []), f"newest={newest}")
    edits.validate(packageName=PACKAGE, editId=eid).execute()
    check("Edit validates", True)
finally:
    edits.delete(packageName=PACKAGE, editId=eid).execute()
print("API-visible items ready" if ok else "Some API-visible items need attention")
