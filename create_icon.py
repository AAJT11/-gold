from PIL import Image, ImageDraw, ImageFont
import os

def create_icon():
    sizes = [16, 24, 32, 48, 64, 128, 256]
    images = []

    for size in sizes:
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)

        # Draw gold coin / income badge
        margin = max(1, size // 16)
        outer_bbox = [margin, margin, size - margin, size - margin]

        # Gold gradient simulation / outer border
        draw.ellipse(outer_bbox, fill=(245, 195, 35, 255), outline=(215, 160, 10, 255), width=max(1, size // 16))

        # Inner ring
        inner_margin = max(2, size // 6)
        inner_bbox = [inner_margin, inner_margin, size - inner_margin, size - inner_margin]
        draw.ellipse(inner_bbox, outline=(210, 155, 10, 180), width=max(1, size // 24))

        # Currency symbol ¥ / $
        try:
            font_size = int(size * 0.5)
            font = ImageFont.truetype("msyh.ttc", font_size) # Microsoft YaHei
        except Exception:
            try:
                font = ImageFont.truetype("arial.ttf", int(size * 0.5))
            except Exception:
                font = ImageFont.load_default()

        text = "¥"
        # Center text
        bbox = draw.textbbox((0, 0), text, font=font)
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]
        x = (size - w) / 2 - bbox[0]
        y = (size - h) / 2 - bbox[1] - (size // 30)

        draw.text((x, y), text, font=font, fill=(70, 45, 0, 255))
        images.append(img)

    # Save PNG & ICO
    images[-1].save("app_icon.png")
    images[0].save("app_icon.ico", format="ICO", sizes=[(s, s) for s in sizes], append_images=images[1:])
    print("app_icon.ico and app_icon.png created successfully!")

if __name__ == "__main__":
    create_icon()
