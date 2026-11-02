from __future__ import annotations
import winreg
from dataclasses import dataclass
from typing import Optional
from PyQt6.QtCore import Qt, QRectF, QPointF
from PyQt6.QtGui import (
    QPainter, QColor, QLinearGradient, QPen, QBrush,
    QPainterPath, QPixmap, QImage
)
from PIL import Image, ImageGrab, ImageFilter

_SCREEN_CACHE: Optional[Image.Image] = None


def set_exclude_from_capture(hwnd: int, exclude: bool = True):
    """No-op kept for API compatibility."""
    pass


def refresh_screen_cache(exclude_rects: Optional[list] = None):
    """
    Captures the desktop once right before a Popover or Dialog is shown (while hidden)
    for the 20px-28px Luxury Liquid Glass backdrop-filter blur.
    """
    global _SCREEN_CACHE
    try:
        _SCREEN_CACHE = ImageGrab.grab().convert("RGB")
    except Exception:
        pass


def capture_frosted_backdrop(
    x: int,
    y: int,
    w: int,
    h: int,
    blur_radius: float = 24.0
) -> Optional[QPixmap]:
    """
    Crops the background behind Popover / SettingsDialog and applies a 24px
    Gaussian blur matching `.luxury-ecommerce .liquid-glass { backdrop-filter: blur(20px) }`.
    """
    global _SCREEN_CACHE
    if w <= 2 or h <= 2:
        return None
    try:
        if _SCREEN_CACHE is None:
            refresh_screen_cache()
        if _SCREEN_CACHE is None:
            return None

        sw, sh = _SCREEN_CACHE.size
        cx1 = max(0, min(sw - 2, int(x)))
        cy1 = max(0, min(sh - 2, int(y)))
        cx2 = max(cx1 + 1, min(sw, int(x + w)))
        cy2 = max(cy1 + 1, min(sh, int(y + h)))
        img = _SCREEN_CACHE.crop((cx1, cy1, cx2, cy2))
        if img.size != (w, h):
            img = img.resize((w, h), Image.Resampling.BILINEAR)

        small_w = max(8, w // 4)
        small_h = max(8, h // 4)
        small = img.resize((small_w, small_h), Image.Resampling.BILINEAR)
        blurred_small = small.filter(ImageFilter.GaussianBlur(radius=max(5.5, blur_radius / 3.5)))
        blurred = blurred_small.resize((w, h), Image.Resampling.BICUBIC)

        data = blurred.tobytes("raw", "RGB")
        qimg = QImage(data, w, h, w * 3, QImage.Format.Format_RGB888).copy()
        return QPixmap.fromImage(qimg)
    except Exception:
        return None


def is_system_dark_mode() -> bool:
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"
        )
        val, _ = winreg.QueryValueEx(key, "SystemUsesLightTheme")
        winreg.CloseKey(key)
        return val == 0
    except Exception:
        return False


def is_apps_dark_mode() -> bool:
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"
        )
        val, _ = winreg.QueryValueEx(key, "AppsUseLightTheme")
        winreg.CloseKey(key)
        return val == 0
    except Exception:
        return False


@dataclass
class ThemeColors:
    is_dark: bool
    bg_capsule: str
    border_capsule: str
    text_primary: str
    text_secondary: str
    popover_bg: str
    popover_border: str
    card_bg: str
    divider: str
    progress_bg: str
    progress_fill: str
    accent: str
    hover_bg: str


def get_theme_colors(dark: bool | None = None) -> ThemeColors:
    """
    Tokens from https://uupm.cc/demo/luxury-ecommerce & MASTER.md:
      --primary: #1C1917 (Obsidian Black)
      --secondary / --cta: #C5A059 / #D4AF37 (Champagne Gold)
      --bg: #FFFFFF
      --bg-cream: #FAF9F6 (Warm Alabaster Cream)
      --text: #1A1A1A
      --text-muted: #57534E
      --border: #E5E5E5 / #D6D3D1
    """
    if dark is None:
        dark = is_apps_dark_mode()

    if dark:
        return ThemeColors(
            is_dark=True,
            bg_capsule="rgba(28, 25, 23, 0.92)",
            border_capsule="rgba(212, 175, 55, 0.45)",
            text_primary="#FAF9F6",
            text_secondary="#A8A29E",
            popover_bg="#1C1917",
            popover_border="rgba(212, 175, 55, 0.35)",
            card_bg="rgba(255, 255, 255, 0.05)",
            divider="rgba(212, 175, 55, 0.16)",
            progress_bg="rgba(255, 255, 255, 0.12)",
            progress_fill="#D4AF37",
            accent="#D4AF37",
            hover_bg="rgba(212, 175, 55, 0.15)"
        )
    else:
        return ThemeColors(
            is_dark=False,
            bg_capsule="rgba(250, 249, 246, 0.94)",
            border_capsule="rgba(197, 160, 89, 0.55)",
            text_primary="#1A1A1A",
            text_secondary="#57534E",
            popover_bg="#FAF9F6",
            popover_border="#D6D3D1",
            card_bg="#FFFFFF",
            divider="#E5E5E5",
            progress_bg="#E7E5E4",
            progress_fill="#C5A059",
            accent="#A16207",
            hover_bg="#F5F3EF"
        )


def paint_liquid_glass_pill(
    painter: QPainter,
    rect: QRectF,
    is_dark: bool,
    is_hovered: bool = False,
    hover_pos: Optional[QPointF] = None,
    backdrop: Optional[QPixmap] = None
):
    """
    Paints the Status Bar Capsule in the `uupm.cc/demo/luxury-ecommerce` style:
    - Warm Alabaster Cream (#FAF9F6) or Obsidian (#1C1917) Liquid Glass pill
    - Top optical glass specular highlight
    - Champagne Gold (#C5A059 / #D4AF37) hairline border
    """
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    radius = rect.height() / 2.0
    pill_path = QPainterPath()
    pill_path.addRoundedRect(rect, radius, radius)

    # 1. Soft luxury ambient shadow (box-shadow: 0 4px 16px rgba(0,0,0,0.08))
    shadow_rect = rect.adjusted(0.5, 1.0, -0.5, 1.0)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor(28, 25, 23, 45 if is_dark else 22))
    painter.drawRoundedRect(shadow_rect, radius, radius)

    painter.save()
    painter.setClipPath(pill_path)

    # 2. Warm Alabaster Cream (--bg-cream: #FAF9F6) / Obsidian (#1C1917) Liquid Glass Base
    grad = QLinearGradient(rect.topLeft(), rect.bottomLeft())
    if is_dark:
        grad.setColorAt(0.0, QColor(36, 32, 29, 246 if is_hovered else 238))
        grad.setColorAt(1.0, QColor(24, 21, 19, 242 if is_hovered else 232))
    else:
        grad.setColorAt(0.0, QColor(255, 255, 255, 250 if is_hovered else 242))
        grad.setColorAt(1.0, QColor(250, 249, 246, 246 if is_hovered else 236))
    painter.fillPath(pill_path, QBrush(grad))

    # 3. Top Liquid Glass Specular Sheen
    top_sheen = QLinearGradient(0, rect.top(), 0, rect.top() + rect.height() * 0.45)
    if is_dark:
        top_sheen.setColorAt(0.0, QColor(212, 175, 55, 45 if is_hovered else 28))
        top_sheen.setColorAt(1.0, QColor(212, 175, 55, 0))
    else:
        top_sheen.setColorAt(0.0, QColor(255, 255, 255, 210))
        top_sheen.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), rect.height() * 0.45), QBrush(top_sheen))

    painter.restore()

    # 4. Champagne Gold + Ivory Double-Feel Rim
    rim_grad = QLinearGradient(rect.topLeft(), rect.bottomRight())
    if is_dark:
        rim_grad.setColorAt(0.0, QColor(212, 175, 55, 160 if is_hovered else 110))
        rim_grad.setColorAt(0.5, QColor(255, 255, 255, 45))
        rim_grad.setColorAt(1.0, QColor(197, 160, 89, 130 if is_hovered else 85))
    else:
        rim_grad.setColorAt(0.0, QColor(197, 160, 89, 175 if is_hovered else 125))
        rim_grad.setColorAt(0.5, QColor(214, 211, 209, 160))
        rim_grad.setColorAt(1.0, QColor(161, 98, 7, 150 if is_hovered else 95))

    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.setPen(QPen(QBrush(rim_grad), 1.1))
    painter.drawRoundedRect(rect, radius, radius)

    painter.restore()


def paint_liquid_glass_panel(
    painter: QPainter,
    rect: QRectF,
    radius: float,
    is_dark: bool,
    backdrop: Optional[QPixmap] = None,
    hover_pos: Optional[QPointF] = None,
    backdrop_offset: QPointF = QPointF(0, 0)
):
    """
    Paints the Popover & Settings Window in the `uupm.cc/demo/luxury-ecommerce` style:
      .liquid-glass { backdrop-filter: blur(20px); background: #ffffffb3; border: 1px solid #ffffff4d; }
      Combined with --bg-cream (#FAF9F6), --border (#E5E5E5), and Champagne Gold accents (#C5A059).
    """
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    panel_path = QPainterPath()
    panel_path.addRoundedRect(rect, radius, radius)

    painter.save()
    painter.setClipPath(panel_path)

    # 1. 20px-24px Blurred Backdrop
    if backdrop and not backdrop.isNull():
        painter.drawPixmap(rect.toRect(), backdrop)

    # 2. Warm Alabaster Cream (--bg-cream: #FAF9F6) / Obsidian (#1C1917) Liquid Glass Tint
    bg_grad = QLinearGradient(rect.topLeft(), rect.bottomRight())
    if is_dark:
        alpha = 234 if (backdrop and not backdrop.isNull()) else 248
        bg_grad.setColorAt(0.0, QColor(28, 25, 23, alpha))
        bg_grad.setColorAt(0.5, QColor(24, 21, 19, alpha + 4))
        bg_grad.setColorAt(1.0, QColor(20, 18, 16, alpha + 6))
    else:
        alpha = 234 if (backdrop and not backdrop.isNull()) else 250
        bg_grad.setColorAt(0.0, QColor(255, 255, 255, alpha + 4))
        bg_grad.setColorAt(0.45, QColor(250, 249, 246, alpha))
        bg_grad.setColorAt(1.0, QColor(245, 243, 239, alpha + 4))
    painter.fillPath(panel_path, QBrush(bg_grad))

    # 3. Top Liquid Glass Specular Reflection
    top_gloss = QLinearGradient(0, rect.top(), 0, rect.top() + 56)
    if is_dark:
        top_gloss.setColorAt(0.0, QColor(212, 175, 55, 24))
        top_gloss.setColorAt(1.0, QColor(212, 175, 55, 0))
    else:
        top_gloss.setColorAt(0.0, QColor(255, 255, 255, 180))
        top_gloss.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), 56), QBrush(top_gloss))

    # 4. Signature Luxury Champagne Gold Top Hairline Bar
    gold_bar = QLinearGradient(rect.left() + 24, rect.top(), rect.right() - 24, rect.top())
    gold_bar.setColorAt(0.0, QColor(197, 160, 89, 0))
    gold_bar.setColorAt(0.25, QColor(197, 160, 89, 160))
    gold_bar.setColorAt(0.5, QColor(212, 175, 55, 235))
    gold_bar.setColorAt(0.75, QColor(197, 160, 89, 160))
    gold_bar.setColorAt(1.0, QColor(197, 160, 89, 0))
    painter.fillRect(QRectF(rect.left() + 24, rect.top(), rect.width() - 48, 2.0), QBrush(gold_bar))

    painter.restore()

    # 5. Inner Liquid Glass Specular Rim (1px inset)
    inner_rect = rect.adjusted(1.0, 1.0, -1.0, -1.0)
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.setPen(QPen(QColor(255, 255, 255, 28 if is_dark else 210), 1.0))
    painter.drawRoundedRect(inner_rect, max(1.0, radius - 1.0), max(1.0, radius - 1.0))

    # 6. Outer Architectural Hairline Border (--border: #D6D3D1 + subtle gold warmth)
    outer_grad = QLinearGradient(rect.topLeft(), rect.bottomRight())
    if is_dark:
        outer_grad.setColorAt(0.0, QColor(212, 175, 55, 95))
        outer_grad.setColorAt(0.5, QColor(255, 255, 255, 35))
        outer_grad.setColorAt(1.0, QColor(197, 160, 89, 75))
    else:
        outer_grad.setColorAt(0.0, QColor(197, 160, 89, 115))
        outer_grad.setColorAt(0.5, QColor(214, 211, 209, 210))
        outer_grad.setColorAt(1.0, QColor(197, 160, 89, 95))
    painter.setPen(QPen(QBrush(outer_grad), 1.0))
    painter.drawRoundedRect(rect, radius, radius)

    painter.restore()
