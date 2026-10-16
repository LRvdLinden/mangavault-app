#!/usr/bin/env python3
"""Render the README screenshots from the HTML sources in this folder.

Usage:  python3 render.py            # all screens
        python3 render.py home cards # only these

Every page is rendered at 390x844 CSS px with deviceScaleFactor 3 (1170x2532 PNG)
and written to ../<name>.png. Needs `pip install playwright`; set
PLAYWRIGHT_BROWSERS_PATH if Chromium lives somewhere non-standard.
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

SRC = Path(__file__).resolve().parent
OUT = SRC.parent
SCREENS = ["home", "catalog", "collection", "manga", "volume", "rewards", "cards", "events", "profile", "friends"]


def main(names):
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx = browser.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=3, color_scheme="dark")
        page = ctx.new_page()
        for name in names:
            page.goto((SRC / f"{name}.html").as_uri())
            page.wait_for_load_state("networkidle")
            page.evaluate("document.fonts.ready")
            out = OUT / f"{name}.png"
            page.screenshot(path=str(out), clip={"x": 0, "y": 0, "width": 390, "height": 844})
            print("wrote", out)
        browser.close()


if __name__ == "__main__":
    main(sys.argv[1:] or SCREENS)
