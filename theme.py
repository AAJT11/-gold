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
    """Captures desktop for soft underlying depth if needed."""
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
    """Provides soft ambient backdrop depth for claymorphism cards."""
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
    """
    Playful Educational Claymorphism tokens:
    Option C: Mint Green & Honey Amber (清新薄荷与琥珀蜜糖)
      --primary: #10B981 (Fresh Mint)
      --secondary: #F59E0B (Honey Amber)
      --accent: #D97706
      --text: #064E3B (Deep Forest/Mint Slate)
      --bg: #F0FDF4 / #FFFFFF
      --border: #A7F3D0 (Chunky Clay Border)
    """
    if dark is None:
        dark = is_apps_dark_mode()

    if dark:
        return ThemeColors(
            is_dark=True,
            bg_capsule="rgba(17, 51, 41, 0.95)",
            border_capsule="rgba(52, 211, 153, 0.50)",
            text_primary="#ECFDF5",
            text_secondary="#A7F3D0",
            popover_bg="#0F2E24",
            popover_border="rgba(52, 211, 153, 0.45)",
            card_bg="rgba(19, 56, 45, 0.95)",
            divider="rgba(52, 211, 153, 0.20)",
            progress_bg="rgba(6, 78, 59, 0.40)",
            progress_fill="#FBBF24",
            accent="#F59E0B",
            hover_bg="rgba(52, 211, 153, 0.18)"
        )
    else:
        return ThemeColors(
            is_dark=False,
            bg_capsule="rgba(255, 255, 255, 0.96)",
            border_capsule="#A7F3D0",
            text_primary="#064E3B",
            text_secondary="#047857",
            popover_bg="#F0FDF4",
            popover_border="#A7F3D0",
            card_bg="#FFFFFF",
            divider="#D1FAE5",
            progress_bg="#E6F4EA",
            progress_fill="#F59E0B",
            accent="#F59E0B",
            hover_bg="#ECFDF5"
        )


def paint_clay_pill(
    painter: QPainter,
    rect: QRectF,
    is_dark: bool,
    is_hovered: bool = False,
    hover_pos: Optional[QPointF] = None,
    backdrop: Optional[QPixmap] = None
):
    """
    Paints a Bubbly 3D Claymorphism Capsule Pill (Mint & Honey Amber):
    - Soft double drop shadows (ambient depth)
    - Puffy 3D marshmallow base gradient
    - Inset top-left specular highlight (clay curvature)
    - Chunky rounded mint border (2.0px)
    """
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    radius = rect.height() / 2.0
    pill_path = QPainterPath()
    pill_path.addRoundedRect(rect, radius, radius)

    # 1. Soft diffused outer drop shadow
    shadow_rect = rect.adjusted(0.5, 1.2, -0.5, 1.8)
    shadow_color = QColor(6, 78, 59, 50 if is_dark else 24)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QBrush(shadow_color))
    painter.drawRoundedRect(shadow_rect, radius, radius)

    painter.save()
    painter.setClipPath(pill_path)

    # 2. Puffy Clay Base Gradient (White Mint Clay or Deep Emerald Clay)
    grad = QLinearGradient(rect.topLeft(), rect.bottomLeft())
    if is_dark:
        c_top = QColor(22, 60, 48, 250 if is_hovered else 242)
        c_bot = QColor(12, 38, 30, 252 if is_hovered else 246)
    else:
        c_top = QColor(255, 255, 255, 255 if is_hovered else 252)
        c_bot = QColor(236, 253, 245, 252 if is_hovered else 246)
    grad.setColorAt(0.0, c_top)
    grad.setColorAt(1.0, c_bot)
    painter.fillPath(pill_path, QBrush(grad))

    # 3. Inset 3D Clay Highlight (Top soft puffy sheen)
    sheen_h = rect.height() * 0.48
    sheen_grad = QLinearGradient(0, rect.top(), 0, rect.top() + sheen_h)
    if is_dark:
        sheen_grad.setColorAt(0.0, QColor(52, 211, 153, 55 if is_hovered else 35))
        sheen_grad.setColorAt(1.0, QColor(52, 211, 153, 0))
    else:
        sheen_grad.setColorAt(0.0, QColor(255, 255, 255, 220 if is_hovered else 190))
        sheen_grad.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), sheen_h), QBrush(sheen_grad))

    # 4. Bottom-Right Clay Depth Inset Cushion
    bottom_inset = QLinearGradient(0, rect.bottom() - rect.height() * 0.4, 0, rect.bottom())
    if is_dark:
        bottom_inset.setColorAt(0.0, QColor(0, 0, 0, 0))
        bottom_inset.setColorAt(1.0, QColor(0, 0, 0, 60))
    else:
        bottom_inset.setColorAt(0.0, QColor(16, 185, 129, 0))
        bottom_inset.setColorAt(1.0, QColor(16, 185, 129, 28))
    painter.fillRect(QRectF(rect.left(), rect.bottom() - rect.height() * 0.4, rect.width(), rect.height() * 0.4), QBrush(bottom_inset))

    painter.restore()

    # 5. Chunky Claymorphism Border (2.0px with gentle gradient)
    border_pen_w = 1.8
    border_rect = rect.adjusted(border_pen_w / 2.0, border_pen_w / 2.0, -border_pen_w / 2.0, -border_pen_w / 2.0)
    rim_grad = QLinearGradient(border_rect.topLeft(), border_rect.bottomRight())
    if is_dark:
        rim_grad.setColorAt(0.0, QColor(52, 211, 153, 160 if is_hovered else 120))
        rim_grad.setColorAt(0.5, QColor(16, 185, 129, 130))
        rim_grad.setColorAt(1.0, QColor(5, 150, 105, 110))
    else:
        rim_grad.setColorAt(0.0, QColor(167, 243, 208, 255 if is_hovered else 235))
        rim_grad.setColorAt(0.5, QColor(110, 231, 183, 240))
        rim_grad.setColorAt(1.0, QColor(52, 211, 153, 220))

    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.setPen(QPen(QBrush(rim_grad), border_pen_w))
    painter.drawRoundedRect(border_rect, radius - border_pen_w / 2.0, radius - border_pen_w / 2.0)

    painter.restore()


# Keep alias for compatibility
paint_liquid_glass_pill = paint_clay_pill


def paint_clay_panel(
    painter: QPainter,
    rect: QRectF,
    radius: float,
    is_dark: bool,
    backdrop: Optional[QPixmap] = None,
    hover_pos: Optional[QPointF] = None,
    backdrop_offset: QPointF = QPointF(0, 0)
):
    """
    Paints Bubbly 3D Claymorphism Card Panels (Popover & Settings):
    - 22px-26px soft rounded corners
    - Multi-tone fresh mint white clay base
    - Soft puffy 3D top-left light sheen
    - Warm honey amber & mint organic decorative top accent
    - Chunky tactile border (2.4px)
    """
    painter.save()
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

    panel_path = QPainterPath()
    panel_path.addRoundedRect(rect, radius, radius)

    painter.save()
    painter.setClipPath(panel_path)

    # 1. Base Puffy Clay Gradient
    bg_grad = QLinearGradient(rect.topLeft(), rect.bottomRight())
    if is_dark:
        bg_grad.setColorAt(0.0, QColor(19, 56, 45, 252))
        bg_grad.setColorAt(0.5, QColor(14, 42, 34, 254))
        bg_grad.setColorAt(1.0, QColor(10, 30, 24, 255))
    else:
        bg_grad.setColorAt(0.0, QColor(255, 255, 255, 255))
        bg_grad.setColorAt(0.45, QColor(248, 253, 250, 255))
        bg_grad.setColorAt(1.0, QColor(240, 253, 244, 255))
    painter.fillPath(panel_path, QBrush(bg_grad))

    # 2. Top Inset Specular Sheen (Marshmallow 3D Highlight)
    top_sheen_h = 56.0
    top_sheen = QLinearGradient(0, rect.top(), 0, rect.top() + top_sheen_h)
    if is_dark:
        top_sheen.setColorAt(0.0, QColor(52, 211, 153, 38))
        top_sheen.setColorAt(1.0, QColor(52, 211, 153, 0))
    else:
        top_sheen.setColorAt(0.0, QColor(255, 255, 255, 210))
        top_sheen.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.fillRect(QRectF(rect.left(), rect.top(), rect.width(), top_sheen_h), QBrush(top_sheen))

    # 3. Bottom Cushion Inset Shadow
    bot_shadow_h = 36.0
    bot_shadow = QLinearGradient(0, rect.bottom() - bot_shadow_h, 0, rect.bottom())
    if is_dark:
        bot_shadow.setColorAt(0.0, QColor(0, 0, 0, 0))
        bot_shadow.setColorAt(1.0, QColor(0, 0, 0, 55))
    else:
        bot_shadow.setColorAt(0.0, QColor(5, 150, 105, 0))
        bot_shadow.setColorAt(1.0, QColor(5, 150, 105, 24))
    painter.fillRect(QRectF(rect.left(), rect.bottom() - bot_shadow_h, rect.width(), bot_shadow_h), QBrush(bot_shadow))

    # 4. Honey Amber + Fresh Mint Top Decorative Clay Stripe
    accent_bar = QLinearGradient(rect.left() + 28, rect.top(), rect.right() - 28, rect.top())
    accent_bar.setColorAt(0.0, QColor(245, 158, 11, 0))
    accent_bar.setColorAt(0.2, QColor(245, 158, 11, 180))
    accent_bar.setColorAt(0.5, QColor(16, 185, 129, 235))
    accent_bar.setColorAt(0.8, QColor(245, 158, 11, 180))
    accent_bar.setColorAt(1.0, QColor(245, 158, 11, 0))
    painter.fillRect(QRectF(rect.left() + 28, rect.top(), rect.width() - 56, 3.0), QBrush(accent_bar))

    painter.restore()

    # 5. Chunky Tactile Outer Clay Border (2.2px)
    border_pen_w = 2.2
    border_rect = rect.adjusted(border_pen_w / 2.0, border_pen_w / 2.0, -border_pen_w / 2.0, -border_pen_w / 2.0)
    outer_grad = QLinearGradient(border_rect.topLeft(), border_rect.bottomRight())
    if is_dark:
        outer_grad.setColorAt(0.0, QColor(52, 211, 153, 130))
        outer_grad.setColorAt(0.5, QColor(16, 185, 129, 100))
        outer_grad.setColorAt(1.0, QColor(5, 150, 105, 90))
    else:
        outer_grad.setColorAt(0.0, QColor(167, 243, 208, 255))
        outer_grad.setColorAt(0.5, QColor(110, 231, 183, 240))
        outer_grad.setColorAt(1.0, QColor(52, 211, 153, 220))

    painter.setPen(QPen(QBrush(outer_grad), border_pen_w))
    painter.setBrush(Qt.BrushStyle.NoBrush)
    painter.drawRoundedRect(border_rect, radius - border_pen_w / 2.0, radius - border_pen_w / 2.0)

    painter.restore()


# Keep alias for compatibility
paint_liquid_glass_panel = paint_clay_panel
