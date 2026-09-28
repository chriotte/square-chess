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
FULL = """Square Chess is built for square and near-square screens, compact phones, and devices with physical QWERTY keyboards.

Most chess apps are designed around tall smartphone displays, leaving a relatively small board on compact devices. Square Chess takes the opposite approach: it gives the board as much of the screen as possible, with an interface designed specifically for 1:1, 9:10 and other compact display shapes.

If your phone has a physical keyboard, you can also enter moves directly using chess notation.

Square Chess is a focused, distraction-free chess app:

• Play against the computer at ten difficulty levels, from beginner-friendly play to full-strength Fairy-Stockfish. The engine runs entirely on your phone.
• Play with a friend on the same device, with an optional chess clock.
• Record games played on a real board, then review them move by move.
• Use the standalone chess clock for over-the-board games without needing to start a game in the app.
• Enter moves with the keyboard using notation such as e4, Nf3 or e2e4, or play normally by tapping or dragging pieces.
• Review and manage games with move history, undo, resign and draw handling.
• Export your complete game collection as PGN for use with Lichess, ChessBase and other chess software.
• Make the board your own with board colours, legal-move indicators, move sounds and vibration.

No ads. No account. No Internet permission.

Your games and settings stay on your device. Square Chess is designed to feel fast, simple and at home on compact Android phones where conventional chess apps often do not fit well."""

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
