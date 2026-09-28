"""Uploads the Square Chess en-GB store listing, contact details and graphics."""
import sys
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

KEY = r"~/SquareChessSigning\play-service-account.json"
PACKAGE = "com.dataespresso.squarechess"
LANG = "en-GB"
ART = Path(r"~\Documents\Codex\2026-09-25\ge\outputs\square-chess\design\play-store")
ICON = Path(r"~\Documents\Codex\2026-09-25\ge\outputs\square-chess\design\play-store-icon.png")

TITLE = "Square Chess"
SHORT = "Chess made for square 1:1 screens and keyboard phones. Offline, no ads."
FULL = """Square Chess is made for square and near-square screens (1:1 and 9:10 displays), small-screen compact phones and phones with a physical QWERTY keyboard. Most chess apps are designed for tall phones and leave a small board. Square Chess fills your square screen with the largest possible board, and you can type your moves on the keyboard.

It is a calm, offline chess app:

• Play the computer at ten levels, from a true beginner to full strength. The engine is Fairy-Stockfish and runs on your phone.
• Play a friend on one phone, with an optional chess clock.
• Record a game that you play on a real board, then review it move by move.
• A separate chess clock for over-the-board games.
• Type moves on a keyboard (e4, Nf3, e2e4) or tap and drag pieces.
• Review, undo, resign and claim draws.
• Export all your games as one PGN file for Lichess, ChessBase or other chess apps.
• Board colours, move sounds, vibration and legal-move markers.

No ads. No account. No Internet permission. Your games stay on your phone."""

assert len(TITLE) <= 30 and len(SHORT) <= 80 and len(FULL) <= 4000, (len(TITLE), len(SHORT), len(FULL))

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
edits = api.edits()
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
    replace("icon", [ICON])
    replace("featureGraphic", [ART / "feature-graphic-1024x500.png"])
    replace("phoneScreenshots", sorted(ART.glob("phone-*.png")))
    edits.validate(packageName=PACKAGE, editId=eid).execute()
    try:
        result = edits.commit(packageName=PACKAGE, editId=eid).execute()
    except HttpError as e:
        if "changesNotSentForReview" not in e.content.decode():
            raise
        result = edits.commit(packageName=PACKAGE, editId=eid, changesNotSentForReview=True).execute()
        print("committed without sending for review")
    print("committed edit", result.get("id"))
except HttpError as e:
    print("FAILED:", e.status_code, e.content.decode()[:1500])
    edits.delete(packageName=PACKAGE, editId=eid).execute()
    sys.exit(1)
