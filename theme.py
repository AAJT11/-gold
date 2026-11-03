from __future__ import annotations
import winreg
from dataclasses import dataclass
from typing import Optional
from PyQt6.QtCore import Qt, QRectF, QPointF
from PyQt6.QtGui import (
    QPainter, QColor, QLinearGradient, QRadialGradient, QPen, QBrush,
    QPainterPath, QPixmap, QImage
)
from PIL import Image, ImageGrab, ImageFilter

_SCREEN_CACHE: Optional[Image.Image] = None


def set_exclude_from_capture(hwnd: int, exclude: bool = True):
    """No-op kept for API compatibility."""
    pass


def refresh_screen_cache(exclude_rects: Optional[list] = None):
    """Captures desktop for native frosted blur backdrop."""
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
    """Captures background behind the popover and applies Gaussian blur."""
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
        blurred_small = small.filter(ImageFilter.GaussianBlur(radius=max(6.0, blur_radius / 3.0)))
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
    """macOS Native Frosted Material & Clean System Tokens."""
    if dark is None:
        dark = is_apps_dark_mode()

    if dark:
        return ThemeColors(
            is_dark=True,
            bg_capsule="rgba(36, 36, 36, 0.90)",
            border_capsule="rgba(255, 255, 255, 0.12)",
            text_primary="#F9FAFB",
            text_secondary="#9CA3AF",
            popover_bg="rgba(30, 30, 30, 0.92)",
            popover_border="rgba(255, 255, 255, 0.14)",
            card_bg="rgba(42, 42, 42, 0.85)",
            divider="rgba(255, 255, 255, 0.10)",
            progress_bg="rgba(255, 255, 255, 0.12)",
            progress_fill="#E5E7EB",
            accent="#F59E0B",
            hover_bg="rgba(255, 255, 255, 0.08)"
        )
    else:
        return ThemeColors(
            is_dark=False,
            bg_capsule="rgba(255, 255, 255, 0.92)",
            border_capsule="rgba(0, 0, 0, 0.08)",
            text_primary="#111827",
            text_secondary="#6B7280",
            popover_bg="rgba(255, 255, 255, 0.92)",
            popover_border="rgba(0, 0, 0, 0.08)",
            card_bg="rgba(255, 255, 255, 0.96)",
            divider="rgba(0, 0, 0, 0.07)",
            progress_bg="rgba(0, 0, 0, 0.06)",
            progress_fill="#374151",
            accent="#F59E0B",
            hover_bg="rgba(0, 0, 0, 0.04)"
        )


def paint_native_capsule_pill(
    painter: QPainter,
    rect: QRectF,
    is_dark: bool,
    is_hovered: bool = False,
    hover_pos: Optional[QPointF] = None,
    backdrop: Optional[QPixmap] = None
):
    """Paints a clean, frosted native taskbar pill."""
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    radius = rect.height() / 2.0
    pill_path = QPainterPath()
    pill_path.addRoundedRect(rect, radius, radius)

    # Ambient subtle shadow
    shadow_rect = rect.adjusted(0.0, 1.0, 0.0, 1.5)
    shadow_color = QColor(0, 0, 0, 35 if is_dark else 15)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QBrush(shadow_color))
    painter.drawRoundedRect(shadow_rect, radius, radius)

    painter.save()
    painter.setClipPath(pill_path)

    # Frosted acrylic base fill
    if is_dark:
        bg_col = QColor(36, 36, 36, 240 if is_hovered else 228)
    else:
        bg_col = QColor(255, 255, 255, 248 if is_hovered else 238)
    painter.fillPath(pill_path, QBrush(bg_col))

    # Top light reflection
    sheen_h = rect.height() * 0.45
    sheen = QLinearGradient(0, rect.top(), 0, rect.top() + sheen_h)
    sheen.setColorAt(0.0, QColor(255, 255, 255, 30 if is_dark else 150))
    sheen.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), sheen_h), QBrush(sheen))

    painter.restore()

    # Hairline clean border (0.8px)
    border_col = QColor(255, 255, 255, 35 if is_dark else 0) if is_dark else QColor(0, 0, 0, 26)
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.setPen(QPen(border_col, 0.8))
    painter.drawRoundedRect(rect.adjusted(0.4, 0.4, -0.4, -0.4), radius - 0.4, radius - 0.4)

    painter.restore()


# Keep aliases for backward compatibility
paint_clay_pill = paint_native_capsule_pill
paint_liquid_glass_pill = paint_native_capsule_pill


def paint_native_popover_card(
    painter: QPainter,
    rect: QRectF,
    radius: float,
    is_dark: bool,
    backdrop: Optional[QPixmap] = None,
    arrow_dir: str = "top",  # 'top' or 'bottom' or 'none'
    arrow_x: Optional[float] = None,
    arrow_w: float = 18.0,
    arrow_h: float = 9.0
):
    """
    Paints the native macOS-style frosted popover with top beak arrow pointing to capsule:
    - Pure, frosted glassmorphism background
    - Top triangular beak arrow (points to status bar capsule)
    - 18px continuous rounded corners
    - Crisp hairline border
    """
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    path = QPainterPath()
    if arrow_x is None:
        arrow_x = rect.center().x()
    arrow_x = max(rect.left() + radius + arrow_w / 2.0, min(rect.right() - radius - arrow_w / 2.0, arrow_x))

    if arrow_dir == "top":
        card_top = rect.top() + arrow_h
        card_rect = QRectF(rect.left(), card_top, rect.width(), rect.height() - arrow_h)

        path.moveTo(arrow_x, rect.top())
        path.lineTo(arrow_x + arrow_w / 2.0, card_top)
        path.lineTo(card_rect.right() - radius, card_top)
        path.arcTo(card_rect.right() - radius * 2, card_top, radius * 2, radius * 2, 90, -90)
        path.lineTo(card_rect.right(), card_rect.bottom() - radius)
        path.arcTo(card_rect.right() - radius * 2, card_rect.bottom() - radius * 2, radius * 2, radius * 2, 0, -90)
        path.lineTo(card_rect.left() + radius, card_rect.bottom())
        path.arcTo(card_rect.left(), card_rect.bottom() - radius * 2, radius * 2, radius * 2, 270, -90)
        path.lineTo(card_rect.left(), card_top + radius)
        path.arcTo(card_rect.left(), card_top, radius * 2, radius * 2, 180, -90)
        path.lineTo(arrow_x - arrow_w / 2.0, card_top)
        path.closeSubpath()
    elif arrow_dir == "bottom":
        card_bottom = rect.bottom() - arrow_h
        card_rect = QRectF(rect.left(), rect.top(), rect.width(), rect.height() - arrow_h)

        path.moveTo(card_rect.left() + radius, card_rect.top())
        path.lineTo(card_rect.right() - radius, card_rect.top())
        path.arcTo(card_rect.right() - radius * 2, card_rect.top(), radius * 2, radius * 2, 90, -90)
        path.lineTo(card_rect.right(), card_bottom - radius)
        path.arcTo(card_rect.right() - radius * 2, card_bottom - radius * 2, radius * 2, radius * 2, 0, -90)
        path.lineTo(arrow_x + arrow_w / 2.0, card_bottom)
        path.lineTo(arrow_x, rect.bottom())
        path.lineTo(arrow_x - arrow_w / 2.0, card_bottom)
        path.lineTo(card_rect.left() + radius, card_bottom)
        path.arcTo(card_rect.left(), card_bottom - radius * 2, radius * 2, radius * 2, 270, -90)
        path.lineTo(card_rect.left(), card_rect.top() + radius)
        path.arcTo(card_rect.left(), card_rect.top(), radius * 2, radius * 2, 180, -90)
        path.closeSubpath()
    else:
        path.addRoundedRect(rect, radius, radius)

    painter.save()
    painter.setClipPath(path)

    # 1. Real Blurred Backdrop
    if backdrop and not backdrop.isNull():
        painter.drawPixmap(rect.toRect(), backdrop)

    # 2. Frosted Material Tint
    if is_dark:
        bg_col = QColor(32, 32, 32, 235 if (backdrop and not backdrop.isNull()) else 248)
    else:
        bg_col = QColor(255, 255, 255, 230 if (backdrop and not backdrop.isNull()) else 248)
    painter.fillPath(path, QBrush(bg_col))

    # 3. Soft Top Light Sheen
    top_sheen = QLinearGradient(0, rect.top(), 0, rect.top() + 60)
    top_sheen.setColorAt(0.0, QColor(255, 255, 255, 28 if is_dark else 160))
    top_sheen.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), 60), QBrush(top_sheen))

    painter.restore()

    # 4. Subtle Hairline Border
    border_col = QColor(255, 255, 255, 32) if is_dark else QColor(0, 0, 0, 22)
    painter.setPen(QPen(border_col, 0.8))
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.drawPath(path)

    painter.restore()


# Keep aliases for backward compatibility
paint_clay_panel = paint_native_popover_card
paint_liquid_glass_panel = paint_native_popover_card
