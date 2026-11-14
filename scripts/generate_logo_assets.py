#!/usr/bin/env python3
import os
import sys
from collections import deque
from PIL import Image, ImageDraw

def main():
    src_path = "/home/wickedwizard/.gemini/antigravity/brain/24c7b162-a7f8-4a54-a46b-a48a39db770a/.user_uploaded/media_1794619279708_c8334658.jpg"
    if not os.path.exists(src_path):
        print(f"Error: Source image not found at {src_path}")
        sys.exit(1)

    print(f"Loading {src_path}...")
    orig = Image.open(src_path).convert("RGBA")
    w, h = orig.size
    pixels = orig.load()

    # Step 1: Smooth flood-fill outer background only (preserving internal white elements)
    visited = set()
    queue = deque()
    for x in range(w):
        queue.append((x, 0))
        queue.append((x, h - 1))
        visited.add((x, 0))
        visited.add((x, h - 1))

    for y in range(h):
        queue.append((0, y))
        queue.append((w - 1, y))
        visited.add((0, y))
        visited.add((w - 1, y))

    outer_mask = [[False]*h for _ in range(w)]

    while queue:
        x, y = queue.popleft()
        r, g, b, a = pixels[x, y]
        if r >= 238 and g >= 238 and b >= 238:
            outer_mask[x][y] = True
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in visited:
                    visited.add((nx, ny))
                    queue.append((nx, ny))

    for x in range(w):
        for y in range(h):
            if outer_mask[x][y]:
                r, g, b, a = pixels[x, y]
                min_c = min(r, g, b)
                if min_c >= 250:
                    pixels[x, y] = (r, g, b, 0)
                else:
                    alpha = int(255 * (250 - min_c) / 12.0)
                    alpha = max(0, min(255, alpha))
                    pixels[x, y] = (r, g, b, alpha)

    # Save to Android drawables
    drawable_dir = "android/farmroute-saathi/app/src/main/res/drawable"
    os.makedirs(drawable_dir, exist_ok=True)

    # 1. High-res app_logo.png (512x512)
    orig_512 = orig.resize((512, 512), Image.Resampling.LANCZOS)
    orig_512.save(os.path.join(drawable_dir, "app_logo.png"), "PNG")
    print(f"Saved: {os.path.join(drawable_dir, 'app_logo.png')}")

    # 2. Foreground for adaptive icon (432x432 for 108dp @ 4x xxxhdpi)
    # Inside 108dp adaptive icon, the safe zone is 72dp (66.6%).
    # So we fit the 512 logo into the central ~68% of 432 = 294px.
    adaptive_fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fg_logo_sz = 294
    fg_offset = (432 - fg_logo_sz) // 2
    resized_fg_logo = orig.resize((fg_logo_sz, fg_logo_sz), Image.Resampling.LANCZOS)
    adaptive_fg.paste(resized_fg_logo, (fg_offset, fg_offset), resized_fg_logo)
    adaptive_fg.save(os.path.join(drawable_dir, "ic_launcher_fg_logo.png"), "PNG")
    print(f"Saved: {os.path.join(drawable_dir, 'ic_launcher_fg_logo.png')}")

    # 3. Mipmap launcher icons
    mipmap_sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    res_dir = "android/farmroute-saathi/app/src/main/res"

    for density, size in mipmap_sizes.items():
        density_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(density_dir, exist_ok=True)
        
        logo_sz = int(size * 0.82)
        offset = (size - logo_sz) // 2
        resized_logo = orig.resize((logo_sz, logo_sz), Image.Resampling.LANCZOS)

        # A. Square launcher icon
        icon_square = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        icon_square.paste(resized_logo, (offset, offset), resized_logo)
        icon_square.save(os.path.join(density_dir, "ic_launcher.webp"), "WEBP")

        # B. Round launcher icon
        circle_mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(circle_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        
        icon_round = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        icon_round.paste(resized_logo, (offset, offset), resized_logo)
        
        final_round = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        final_round.paste(icon_round, (0, 0), circle_mask)
        final_round.save(os.path.join(density_dir, "ic_launcher_round.webp"), "WEBP")

        print(f"Generated {density} ({size}x{size}) icons")

    # 4. Web Buyer Dashboard assets
    web_public_dir = "web/buyer_dashboard/public"
    if os.path.exists(web_public_dir):
        orig_128 = orig.resize((128, 128), Image.Resampling.LANCZOS)
        orig_128.save(os.path.join(web_public_dir, "app_logo.png"), "PNG")
        orig_32 = orig.resize((32, 32), Image.Resampling.LANCZOS)
        orig_32.save(os.path.join(web_public_dir, "favicon.png"), "PNG")
        print("Generated web buyer dashboard public assets (app_logo.png, favicon.png)")

    print("All logo assets generated successfully!")

if __name__ == "__main__":
    main()
