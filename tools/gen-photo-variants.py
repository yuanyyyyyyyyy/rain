#!/usr/bin/env python3
"""生成照片响应式变体（幂等，照片更新后重跑即可）：
- public/photos/<pid>-<width>.webp（400/800/1600 档 + 原宽档，超原图宽跳过）
- public/photos/thumbs/<pid>.jpg（640 长边轻量档，srcset 的 jpg 源）
依赖：Pillow（pip install pillow）。"""
import os, sys
from PIL import Image

PHOTOS = os.path.join(os.path.dirname(__file__), "..", "public", "photos")
THUMBS = os.path.join(PHOTOS, "thumbs")
WIDTHS = [400, 800, 1600]
THUMB_EDGE = 640
QUALITY = 80

def main():
    os.makedirs(THUMBS, exist_ok=True)
    made = made_thumb = 0
    for f in sorted(os.listdir(PHOTOS)):
        if not f.endswith(".jpg"):
            continue
        pid = f[:-4]
        img = Image.open(os.path.join(PHOTOS, f)).convert("RGB")
        w, h = img.size

        for target in WIDTHS:
            if target > w:
                continue  # 不放大（=原宽的档保留，srcset 最大档必须真实存在）
            tw = target
            th = round(h * tw / w)
            out = os.path.join(PHOTOS, f"{pid}-{tw}.webp")
            if os.path.exists(out):
                continue
            img.resize((tw, th), Image.LANCZOS).save(out, "WEBP", quality=QUALITY, method=6)
            made += 1
        # 原宽档（横图 w=1600 时即 1600 档本身）：srcset 最大档缺失会让高 DPR 浏览器选中 404 → 空白
        out = os.path.join(PHOTOS, f"{pid}-{w}.webp")
        if not os.path.exists(out):
            img.save(out, "WEBP", quality=QUALITY, method=6)
            made += 1

        # thumbs 轻量档（长边 ≤640，等比）：srcset 的 jpg 源
        thumb = os.path.join(THUMBS, f)
        if not os.path.exists(thumb):
            edge = min(THUMB_EDGE, max(w, h))
            scale = edge / max(w, h)
            tw, th = round(w * scale), round(h * scale)
            img.resize((tw, th), Image.LANCZOS).save(thumb, "JPEG", quality=80)
            made_thumb += 1

    print(f"新增 webp 变体 {made} 个，新增 thumbs {made_thumb} 个（thumbs 现有 {len(os.listdir(THUMBS))} 个）")

if __name__ == "__main__":
    sys.exit(main())
