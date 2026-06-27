#!/usr/bin/env python3
"""
RetailZW SMTP connectivity test
Run on the server:  python3 smtp-test.py
Or with a custom destination: python3 smtp-test.py you@example.com
"""

import smtplib
import ssl
import sys
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from datetime import datetime

# ── Config (matches application.yml) ─────────────────────────────────────────
SMTP_HOST = "mail.retailzw.co.zw"
SMTP_PORT = 587
PASSWORD   = "cHigwagwa1t"

MAILBOXES = [
    {"from": "support@retailzw.co.zw",      "label": "Support"},
    {"from": "sales@retailzw.co.zw",         "label": "Billing / Sales"},
    {"from": "info@retailzw.co.zw",          "label": "Notifications"},
]

SEND_TO = sys.argv[1] if len(sys.argv) > 1 else "talentchigwagwa@gmail.com"
# ─────────────────────────────────────────────────────────────────────────────


def test_smtp_box(from_addr: str, label: str) -> bool:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    subject   = f"[RetailZW SMTP Test] {label} — {timestamp}"
    body_html = f"""
    <div style="font-family:Arial,sans-serif;padding:20px;color:#122033">
      <h2 style="color:#2357d6">✅ SMTP Working — {label}</h2>
      <p>This test message was sent from <strong>{from_addr}</strong>
         via <strong>{SMTP_HOST}:{SMTP_PORT}</strong> using STARTTLS.</p>
      <p><strong>Sent at:</strong> {timestamp}</p>
      <hr>
      <p style="color:#60708a;font-size:12px">RetailZW automated SMTP test</p>
    </div>
    """

    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"]    = f"RetailZW <{from_addr}>"
    msg["To"]      = SEND_TO
    msg["Reply-To"] = "support@retailzw.co.zw"
    msg.attach(MIMEText(body_html, "html", "utf-8"))

    try:
        print(f"\n  Connecting to {SMTP_HOST}:{SMTP_PORT} as {from_addr} ...", end=" ", flush=True)
        ctx = ssl.create_default_context()
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.ehlo()
            server.starttls(context=ctx)
            server.ehlo()
            server.login(from_addr, PASSWORD)
            server.sendmail(from_addr, SEND_TO, msg.as_string())
        print("✅  SENT")
        return True
    except smtplib.SMTPAuthenticationError as e:
        print(f"❌  AUTH FAILED: {e.smtp_code} {e.smtp_error.decode()}")
    except smtplib.SMTPConnectError as e:
        print(f"❌  CONNECT FAILED: {e}")
    except smtplib.SMTPException as e:
        print(f"❌  SMTP ERROR: {e}")
    except OSError as e:
        print(f"❌  NETWORK ERROR: {e}")
    return False


def main():
    print("=" * 60)
    print("  RetailZW SMTP Test")
    print(f"  Host:   {SMTP_HOST}:{SMTP_PORT}  (STARTTLS)")
    print(f"  Target: {SEND_TO}")
    print("=" * 60)

    results = {}
    for box in MAILBOXES:
        results[box["label"]] = test_smtp_box(box["from"], box["label"])

    print("\n" + "=" * 60)
    print("  Results")
    print("=" * 60)
    all_ok = True
    for label, ok in results.items():
        status = "✅  PASS" if ok else "❌  FAIL"
        print(f"  {label:<25} {status}")
        if not ok:
            all_ok = False

    print("=" * 60)
    if all_ok:
        print("  🎉  All mailboxes sent successfully!")
        print(f"  Check {SEND_TO} for 3 test emails.")
    else:
        print("  ⚠️   Some mailboxes failed. Check the errors above.")
        print("\n  Common fixes:")
        print("  1. Wrong password — confirm in cPanel/Hestia that the password is cHigwagwa1t")
        print("  2. SMTP blocked — check if port 587 is open in your firewall")
        print("  3. STARTTLS not enabled — enable in mail server settings")
        print("  4. Hostname mismatch — ensure mail.retailzw.co.zw resolves correctly")


if __name__ == "__main__":
    main()
