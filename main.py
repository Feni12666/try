import asyncio
import logging
import os
import time
from collections import defaultdict, deque
from datetime import datetime, timezone
from html import escape
from pathlib import Path
from urllib.parse import urlparse
from typing import Optional

import aiosqlite
from aiohttp import web
from aiogram import Bot, Dispatcher, F, Router, types
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.exceptions import (
    TelegramBadRequest,
    TelegramForbiddenError,
    TelegramRetryAfter,
)
from aiogram.filters import Command
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.fsm.storage.memory import MemoryStorage
from aiogram.types import (
    BotCommand,
    CallbackQuery,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    InlineQuery,
    InlineQueryResultArticle,
    InputTextMessageContent,
    Message,
    Update,
)

# ==================== CONFIG ====================
BOT_TOKEN = os.getenv("BOT_TOKEN", "").strip()
OFFICIAL_GROUP = os.getenv("OFFICIAL_GROUP", "https://t.me/+FmHy4zqE9CI1YmU0").strip()
backup channel = os.getenv("backup channel", "https://t.me/+ItlwA-0u__4xNTA0").strip()
ADMIN_IDS_ENV = set(map(int, os.getenv("ADMIN_IDS", "").split(","))) if os.getenv("ADMIN_IDS") else set()
RATE_LIMIT_COUNT = int(os.getenv("RATE_LIMIT_COUNT", "6"))
RATE_LIMIT_WINDOW = int(os.getenv("RATE_LIMIT_WINDOW", "10"))
DB_PATH = os.getenv("DB_PATH", "bot.db")
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
WEBHOOK_MODE = os.getenv("WEBHOOK_MODE", "false").lower() == "true"
WEBHOOK_URL = os.getenv("WEBHOOK_URL", "").strip()
WEBHOOK_HOST = os.getenv("WEBHOOK_HOST", "0.0.0.0")
WEBHOOK_PORT = int(os.getenv("PORT", os.getenv("WEBHOOK_PORT", "8443")))
WEBHOOK_PATH = os.getenv("WEBHOOK_PATH", "/webhook").strip()
WEBHOOK_SECRET = os.getenv("WEBHOOK_SECRET", "").strip()
HEALTH_HOST = os.getenv("HEALTH_HOST", "0.0.0.0")
HEALTH_PORT = int(os.getenv("PORT", os.getenv("HEALTH_PORT", "8000")))
BROADCAST_CONCURRENCY = int(os.getenv("BROADCAST_CONCURRENCY", "20"))

# ==================== LOGGING ====================
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("baby-video-bot")

# ==================== GLOBALS ====================
db_conn: Optional[aiosqlite.Connection] = None
db_lock = asyncio.Lock()
admin_ids: set[int] = set(ADMIN_IDS_ENV)
settings_cache: dict[str, str] = {}
start_time = time.time()
broadcast_event: Optional[asyncio.Event] = None
broadcast_task: Optional[asyncio.Task] = None

# ==================== TEXTS ====================
DEFAULT_START_TEXT = f"""
🎬 <b>যারা বাচ্চাদের সুন্দর ভিডিও কালেকশন খুঁজছিলেন, তাদের জন্য আমাদের গ্রুপ 👆</b>

👶🔥 <b>Baby Video Collection ❤️</b>
নতুন নতুন সুন্দর ভিডিও পেতে এখনই জয়েন করুন।

👇 <b>আমাদের Official Group</b> 👇
{escape(OFFICIAL_GROUP)}

👇 <b>Baby Video Collection Group</b> 👇
{escape(BABY_VIDEO_GROUP)}

❤️ <b>সবাইকে স্বাগতম</b>
""".strip()

HELP_TEXT = """
<b>🤖 Bot Help</b>

• /start — গ্রুপের লিংক দেখুন
• /help — সাহায্য দেখুন
• /ping — বট অনলাইন আছে কি না দেখুন
• /id — আপনার Telegram ID দেখুন

নিচের বাটন ব্যবহার করে সরাসরি গ্রুপে জয়েন করতে পারবেন।
""".strip()

ADMIN_PANEL_TEXT = "🔧 <b>Admin Panel</b>\n\nনিচের বাটন থেকে কাজ নির্বাচন করুন。"

# ==================== STATES ====================
class AdminState(StatesGroup):
    waiting_broadcast_text = State()
    waiting_edit_start = State()
    waiting_edit_official = State()
    waiting_edit_baby = State()
    waiting_add_admin = State()
    waiting_remove_admin = State()
    waiting_user_lookup = State()
    waiting_maintenance_message = State()
    waiting_ban_user = State()
    waiting_unban_user = State()

# ==================== DATABASE ====================
async def get_db() -> aiosqlite.Connection:
    global db_conn
    async with db_lock:
        if db_conn is None:
            db_conn = await aiosqlite.connect(DB_PATH)
            await db_conn.execute("PRAGMA journal_mode=WAL")
            await db_conn.execute("PRAGMA busy_timeout=5000")
            await db_conn.execute("PRAGMA foreign_keys=ON")
        return db_conn

async def db_execute(sql: str, params: tuple = (), commit: bool = False):
    db = await get_db()
    async with db_lock:
        cursor = await db.execute(sql, params)
        if commit:
            await db.commit()
    return cursor

async def db_fetch_one(sql: str, params: tuple = ()):
    db = await get_db()
    async with db_lock:
        cursor = await db.execute(sql, params)
        row = await cursor.fetchone()
    return row

async def db_fetch_all(sql: str, params: tuple = ()):
    db = await get_db()
    async with db_lock:
        cursor = await db.execute(sql, params)
        rows = await cursor.fetchall()
    return rows

async def db_close() -> None:
    global db_conn
    async with db_lock:
        if db_conn is not None:
            await db_conn.close()
            db_conn = None

async def init_db() -> None:
    db = await get_db()
    async with db_lock:
        await db.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY,
                username TEXT,
                first_name TEXT,
                last_name TEXT,
                joined_at REAL,
                last_active REAL,
                is_banned INTEGER DEFAULT 0,
                is_dead INTEGER DEFAULT 0,
                last_broadcast_error TEXT
            );
            CREATE TABLE IF NOT EXISTS admins (
                user_id INTEGER PRIMARY KEY,
                added_at REAL,
                added_by INTEGER,
                is_active INTEGER DEFAULT 1
            );
            CREATE TABLE IF NOT EXISTS stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                command TEXT,
                user_id INTEGER,
                timestamp REAL
            );
            CREATE TABLE IF NOT EXISTS broadcasts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                admin_id INTEGER,
                sent_count INTEGER DEFAULT 0,
                failed_count INTEGER DEFAULT 0,
                blocked_count INTEGER DEFAULT 0,
                total_users INTEGER DEFAULT 0,
                type TEXT DEFAULT 'text',
                content TEXT DEFAULT '',
                created_at REAL
            );
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            );
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                admin_id INTEGER,
                action TEXT,
                detail TEXT,
                timestamp REAL
            );
            CREATE INDEX IF NOT EXISTS idx_users_last_active ON users(last_active);
            CREATE INDEX IF NOT EXISTS idx_users_banned_dead ON users(is_banned, is_dead);
            CREATE INDEX IF NOT EXISTS idx_stats_timestamp ON stats(timestamp);
            CREATE INDEX IF NOT EXISTS idx_broadcasts_created ON broadcasts(created_at);
            CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log(timestamp);
        """)
        await db.commit()

    defaults = {
        "official_group": OFFICIAL_GROUP,
        "baby_group": BABY_VIDEO_GROUP,
        "start_text": DEFAULT_START_TEXT,
        "maintenance_mode": "0",
        "maintenance_message": "🔧 Bot is under maintenance. Please try again later.",
    }
    for key, value in defaults.items():
        row = await db_fetch_one("SELECT value FROM settings WHERE key = ?", (key,))
        if row is None:
            await db_execute("INSERT INTO settings (key, value) VALUES (?, ?)", (key, value), commit=True)

    rows = await db_fetch_all("SELECT key, value FROM settings")
    for key, value in rows:
        settings_cache[key] = value

    for admin_id in ADMIN_IDS_ENV:
        await add_admin_db(admin_id, added_by=0)

async def get_setting(key: str) -> str:
    return settings_cache.get(key, "")

async def set_setting(key: str, value: str) -> None:
    settings_cache[key] = value
    await db_execute(
        "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (key, value),
        commit=True,
    )

async def is_maintenance_mode() -> bool:
    return await get_setting("maintenance_mode") == "1"

async def get_maintenance_message() -> str:
    return await get_setting("maintenance_message")

async def add_or_update_user(user_id: int, username: str, first_name: str, last_name: str) -> None:
    now = time.time()
    await db_execute("""
        INSERT INTO users (user_id, username, first_name, last_name, joined_at, last_active)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            username = excluded.username,
            first_name = excluded.first_name,
            last_name = excluded.last_name,
            last_active = excluded.last_active
    """, (user_id, username, first_name, last_name, now, now), commit=True)

async def log_stat(command: str, user_id: int) -> None:
    await db_execute(
        "INSERT INTO stats (command, user_id, timestamp) VALUES (?, ?, ?)",
        (command, user_id, time.time()),
        commit=True,
    )

async def is_banned(user_id: int) -> bool:
    row = await db_fetch_one("SELECT is_banned FROM users WHERE user_id = ?", (user_id,))
    return bool(row[0]) if row else False

async def ban_user(user_id: int) -> None:
    await db_execute(
        "INSERT INTO users (user_id, is_banned) VALUES (?, 1) ON CONFLICT(user_id) DO UPDATE SET is_banned = 1",
        (user_id,),
        commit=True,
    )

async def unban_user(user_id: int) -> None:
    await db_execute("UPDATE users SET is_banned = 0 WHERE user_id = ?", (user_id,), commit=True)

async def mark_user_dead(user_id: int, error: str = "") -> None:
    await db_execute(
        "UPDATE users SET is_dead = 1, last_broadcast_error = ? WHERE user_id = ?",
        (error, user_id),
        commit=True,
    )

async def mark_user_alive(user_id: int) -> None:
    await db_execute(
        "UPDATE users SET is_dead = 0, last_broadcast_error = NULL WHERE user_id = ?",
        (user_id,),
        commit=True,
    )

async def get_all_broadcast_users() -> list[int]:
    rows = await db_fetch_all("SELECT user_id FROM users WHERE is_banned = 0 AND is_dead = 0")
    return [row[0] for row in rows]

async def get_total_users() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM users")
    return row[0] if row else 0

async def get_banned_users() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM users WHERE is_banned = 1")
    return row[0] if row else 0

async def get_dead_users() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM users WHERE is_dead = 1")
    return row[0] if row else 0

async def get_active_24h() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM users WHERE last_active > ?", (time.time() - 86400,))
    return row[0] if row else 0

async def get_active_7d() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM users WHERE last_active > ?", (time.time() - 7 * 86400,))
    return row[0] if row else 0

async def get_new_today() -> int:
    now = time.time()
    start_of_day = now - (now % 86400)
    row = await db_fetch_one("SELECT COUNT(*) FROM users WHERE joined_at >= ?", (start_of_day,))
    return row[0] if row else 0

async def get_total_commands() -> int:
    row = await db_fetch_one("SELECT COUNT(*) FROM stats")
    return row[0] if row else 0

async def get_user_info(user_id: int) -> dict | None:
    row = await db_fetch_one("""
        SELECT user_id, username, first_name, last_name, joined_at, last_active, is_banned, is_dead, last_broadcast_error
        FROM users WHERE user_id = ?
    """, (user_id,))
    if not row:
        return None
    return {
        "user_id": row[0],
        "username": row[1],
        "first_name": row[2],
        "last_name": row[3],
        "joined_at": row[4],
        "last_active": row[5],
        "is_banned": row[6],
        "is_dead": row[7],
        "last_error": row[8],
    }

async def add_admin_db(user_id: int, added_by: int) -> None:
    await db_execute(
        "INSERT INTO admins (user_id, added_at, added_by) VALUES (?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET is_active = 1, added_at = excluded.added_at, added_by = excluded.added_by",
        (user_id, time.time(), added_by),
        commit=True,
    )
    admin_ids.add(user_id)

async def remove_admin_db(user_id: int) -> None:
    await db_execute("UPDATE admins SET is_active = 0 WHERE user_id = ?", (user_id,), commit=True)
    admin_ids.discard(user_id)

async def load_admins() -> None:
    global admin_ids
    rows = await db_fetch_all("SELECT user_id FROM admins WHERE is_active = 1")
    admin_ids = ADMIN_IDS_ENV.copy()
    for row in rows:
        admin_ids.add(row[0])

async def is_admin(user_id: int) -> bool:
    return user_id in admin_ids

async def count_active_admins() -> int:
    return len(admin_ids)

async def add_audit_log(admin_id: int, action: str, detail: str = "") -> None:
    await db_execute(
        "INSERT INTO audit_log (admin_id, action, detail, timestamp) VALUES (?, ?, ?, ?)",
        (admin_id, action, detail, time.time()),
        commit=True,
    )

async def add_broadcast_record(admin_id: int, sent: int, failed: int, blocked: int, total: int, btype: str, content: str) -> None:
    await db_execute(
        "INSERT INTO broadcasts (admin_id, sent_count, failed_count, blocked_count, total_users, type, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (admin_id, sent, failed, blocked, total, btype, content, time.time()),
        commit=True,
    )

async def get_broadcast_history(limit: int = 10) -> list[dict]:
    rows = await db_fetch_all("""
        SELECT id, admin_id, sent_count, failed_count, blocked_count, total_users, type, content, created_at
        FROM broadcasts ORDER BY id DESC LIMIT ?
    """, (limit,))
    return [
        {
            "id": r[0],
            "admin_id": r[1],
            "sent": r[2],
            "failed": r[3],
            "blocked": r[4],
            "total": r[5],
            "type": r[6],
            "content": (r[7][:50] if r[7] else ""),
            "created_at": r[8],
        }
        for r in rows
    ]

# ==================== HELPERS ====================
def is_valid_url(url: str) -> bool:
    try:
        parsed = urlparse(url)
        return parsed.scheme in ("http", "https") and bool(parsed.netloc)
    except Exception:
        return False

def start_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="🔥 Official Group", url=settings_cache.get("official_group", OFFICIAL_GROUP))],
            [InlineKeyboardButton(text="👶 Baby Video Group", url=settings_cache.get("baby_group", BABY_VIDEO_GROUP))],
            [InlineKeyboardButton(text="🔄 Refresh", callback_data="refresh_start")],
        ]
    )

def admin_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="📊 Statistics", callback_data="admin_stats")],
            [InlineKeyboardButton(text="📢 Broadcast", callback_data="admin_broadcast_prompt")],
            [InlineKeyboardButton(text="✏️ Edit Start", callback_data="admin_edit_start")],
            [InlineKeyboardButton(text="🔗 Edit Groups", callback_data="admin_edit_links")],
            [InlineKeyboardButton(text="🔧 Maintenance", callback_data="admin_maintenance_menu")],
            [InlineKeyboardButton(text="👥 Users", callback_data="admin_users_menu")],
            [InlineKeyboardButton(text="➕ Add Admin", callback_data="admin_add_admin")],
            [InlineKeyboardButton(text="➖ Remove Admin", callback_data="admin_remove_admin")],
            [InlineKeyboardButton(text="🔍 User Lookup", callback_data="admin_user_lookup")],
            [InlineKeyboardButton(text="📈 Broadcast History", callback_data="admin_broadcast_history")],
            [InlineKeyboardButton(text="🧹 Clean Dead", callback_data="admin_clean_dead")],
            [InlineKeyboardButton(text="📤 Backup DB", callback_data="admin_backup")],
            [InlineKeyboardButton(text="ℹ️ Uptime", callback_data="admin_uptime")],
        ]
    )

def admin_back_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[[InlineKeyboardButton(text="⬅️ Back to Admin Panel", callback_data="admin_back")]]
    )

def cancel_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[[InlineKeyboardButton(text="❌ Cancel", callback_data="admin_cancel")]]
    )

# ==================== MIDDLEWARES ====================
class BanMiddleware:
    async def __call__(self, handler, event, data):
        user_id = None
        if isinstance(event, Message):
            user_id = event.from_user.id
        elif isinstance(event, CallbackQuery):
            user_id = event.from_user.id
        elif isinstance(event, InlineQuery):
            user_id = event.from_user.id

        if user_id and await is_banned(user_id) and user_id not in admin_ids:
            if isinstance(event, Message):
                await event.answer("⛔ আপনি ব্যান করা আছেন।")
            elif isinstance(event, CallbackQuery):
                await event.answer("⛔ ব্যান করা হয়েছে।", show_alert=True)
            return
        return await handler(event, data)

class RateLimitMiddleware:
    def __init__(self):
        self._state: dict[int, deque[float]] = defaultdict(deque)

    async def __call__(self, handler, event, data):
        user_id = None
        if isinstance(event, Message):
            user_id = event.from_user.id
        elif isinstance(event, CallbackQuery):
            user_id = event.from_user.id
        elif isinstance(event, InlineQuery):
            user_id = event.from_user.id

        if not user_id:
            return await handler(event, data)

        if user_id in admin_ids:
            return await handler(event, data)

        now = time.monotonic()
        q = self._state[user_id]
        while q and now - q[0] > RATE_LIMIT_WINDOW:
            q.popleft()
        if len(q) >= RATE_LIMIT_COUNT:
            if isinstance(event, Message):
                await event.answer("⏳ একটু ধীরে চেষ্টা করুন।")
            elif isinstance(event, CallbackQuery):
                await event.answer("⏳ ধীরে চেষ্টা করুন।", show_alert=True)
            return
        q.append(now)
        return await handler(event, data)

class UserActivityMiddleware:
    async def __call__(self, handler, event, data):
        if isinstance(event, Message) and event.from_user:
            user = event.from_user
            await add_or_update_user(user.id, user.username, user.first_name, user.last_name)
        elif isinstance(event, CallbackQuery) and event.from_user:
            user = event.from_user
            await add_or_update_user(user.id, user.username, user.first_name, user.last_name)
        return await handler(event, data)

class MaintenanceMiddleware:
    async def __call__(self, handler, event, data):
        user_id = None
        if isinstance(event, Message):
            user_id = event.from_user.id
        elif isinstance(event, CallbackQuery):
            user_id = event.from_user.id
        elif isinstance(event, InlineQuery):
            user_id = event.from_user.id

        if user_id and user_id not in admin_ids and await is_maintenance_mode():
            if isinstance(event, Message):
                await event.answer(await get_maintenance_message())
            elif isinstance(event, CallbackQuery):
                await event.answer(await get_maintenance_message(), show_alert=True)
            return
        return await handler(event, data)

# ==================== ROUTER ====================
router = Router()
router.message.middleware(BanMiddleware())
router.message.middleware(RateLimitMiddleware())
router.message.middleware(MaintenanceMiddleware())
router.message.middleware(UserActivityMiddleware())
router.callback_query.middleware(BanMiddleware())
router.callback_query.middleware(RateLimitMiddleware())
router.callback_query.middleware(MaintenanceMiddleware())
router.callback_query.middleware(UserActivityMiddleware())
router.inline_query.middleware(BanMiddleware())
router.inline_query.middleware(RateLimitMiddleware())

# ==================== COMMAND HANDLERS ====================
@router.message(Command("start"))
async def cmd_start(message: Message):
    user = message.from_user
    await log_stat("start", user.id)
    await message.answer(await get_setting("start_text"), reply_markup=start_keyboard(), disable_web_page_preview=True)

@router.message(Command("help"))
async def cmd_help(message: Message):
    await message.answer(HELP_TEXT, reply_markup=start_keyboard(), disable_web_page_preview=True)

@router.message(Command("ping"))
async def cmd_ping(message: Message):
    start = time.perf_counter()
    sent = await message.answer("🏓 Pinging...")
    latency = int((time.perf_counter() - start) * 1000)
    try:
        await sent.edit_text(f"✅ Bot is online\n⚡ Response: {latency} ms")
    except TelegramBadRequest:
        pass

@router.message(Command("id"))
async def cmd_id(message: Message):
    await message.answer(f"🆔 আপনার Telegram ID: <code>{message.from_user.id}</code>")

@router.message(Command("admin"))
async def cmd_admin(message: Message):
    if not await is_admin(message.from_user.id):
        return
    await message.answer(ADMIN_PANEL_TEXT, reply_markup=admin_keyboard())

@router.message(Command("stats"))
async def cmd_stats(message: Message):
    if not await is_admin(message.from_user.id):
        return
    await show_stats(message)

@router.message(Command("ban"))
async def cmd_ban(message: Message):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) != 2 or not parts[1].isdigit():
        await message.answer("Usage: /ban <user_id>")
        return
    target_id = int(parts[1])
    if target_id == message.from_user.id:
        await message.answer("❌ আপনি নিজেকে ব্যান করতে পারবেন না।")
        return
    if await is_admin(target_id):
        await message.answer("❌ অ্যাডমিনকে ব্যান করা যাবে না।")
        return
    await ban_user(target_id)
    await add_audit_log(message.from_user.id, "ban", str(target_id))
    await message.answer(f"✅ User <code>{target_id}</code> banned.")

@router.message(Command("unban"))
async def cmd_unban(message: Message):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) != 2 or not parts[1].isdigit():
        await message.answer("Usage: /unban <user_id>")
        return
    target_id = int(parts[1])
    await unban_user(target_id)
    await add_audit_log(message.from_user.id, "unban", str(target_id))
    await message.answer(f"✅ User <code>{target_id}</code> unbanned.")

@router.message(Command("addadmin"))
async def cmd_addadmin(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) == 2 and parts[1].isdigit():
        target_id = int(parts[1])
        await add_admin_db(target_id, added_by=message.from_user.id)
        await add_audit_log(message.from_user.id, "add_admin", str(target_id))
        await message.answer(f"✅ User <code>{target_id}</code> added as admin.")
    else:
        await message.answer("➕ নতুন অ্যাডমিনের Telegram ID পাঠান অথবা /addadmin <id> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_add_admin)

@router.message(AdminState.waiting_add_admin)
async def process_add_admin(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    if message.text and message.text.isdigit():
        target_id = int(message.text)
        await add_admin_db(target_id, added_by=message.from_user.id)
        await add_audit_log(message.from_user.id, "add_admin", str(target_id))
        await message.answer(f"✅ User <code>{target_id}</code> added as admin.")
        await state.clear()
    else:
        await message.answer("❌ Invalid ID. সংখ্যা পাঠান।")

@router.message(Command("removeadmin"))
async def cmd_removeadmin(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) == 2 and parts[1].isdigit():
        target_id = int(parts[1])
        await remove_admin_target(target_id, message.from_user.id, message)
    else:
        await message.answer("➖ Remove করতে অ্যাডমিনের Telegram ID পাঠান অথবা /removeadmin <id> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_remove_admin)

@router.message(AdminState.waiting_remove_admin)
async def process_remove_admin(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    if message.text and message.text.isdigit():
        target_id = int(message.text)
        await remove_admin_target(target_id, message.from_user.id, message)
        await state.clear()
    else:
        await message.answer("❌ Invalid ID. সংখ্যা পাঠান।")

async def remove_admin_target(target_id: int, actor_id: int, message: Message):
    if target_id == actor_id and await count_active_admins() <= 1:
        await message.answer("❌ আপনি শেষ অ্যাডমিন, নিজেকে রিমুভ করতে পারবেন না।")
        return
    if target_id not in admin_ids:
        await message.answer("❌ এই ইউজার অ্যাডমিন নয়।")
        return
    if await count_active_admins() <= 1:
        await message.answer("❌ শেষ অ্যাডমিনকে রিমুভ করা যাবে না।")
        return
    await remove_admin_db(target_id)
    await add_audit_log(actor_id, "remove_admin", str(target_id))
    await message.answer(f"✅ User <code>{target_id}</code> removed from admin.")

@router.message(Command("lookup"))
async def cmd_lookup(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) == 2 and parts[1].isdigit():
        target_id = int(parts[1])
        await show_user_info(message, target_id)
    else:
        await message.answer("🔍 লুকআপ করতে Telegram ID পাঠান অথবা /lookup <id> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_user_lookup)

@router.message(AdminState.waiting_user_lookup)
async def process_lookup(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    if message.text and message.text.isdigit():
        target_id = int(message.text)
        await show_user_info(message, target_id)
        await state.clear()
    else:
        await message.answer("❌ Invalid ID.")

async def show_user_info(message: Message, user_id: int):
    info = await get_user_info(user_id)
    if not info:
        await message.answer("❌ User not found.")
        return
    joined = datetime.fromtimestamp(info["joined_at"], tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC") if info["joined_at"] else "N/A"
    last = datetime.fromtimestamp(info["last_active"], tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC") if info["last_active"] else "N/A"
    text = f"""
👤 <b>User Info</b>
ID: <code>{info['user_id']}</code>
Username: @{info['username'] or "N/A"}
Name: {info['first_name'] or ""} {info['last_name'] or ""}
Joined: {joined}
Last Active: {last}
Banned: {"Yes" if info['is_banned'] else "No"}
Dead: {"Yes" if info['is_dead'] else "No"}
Last Error: {info['last_error'] or "None"}
"""
    await message.answer(text)

@router.message(Command("editstart"))
async def cmd_editstart(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    args = message.text.split(maxsplit=1)
    if len(args) > 1 and args[1].strip():
        new_text = args[1].strip()
        await set_setting("start_text", new_text)
        await add_audit_log(message.from_user.id, "edit_start", new_text[:50])
        await message.answer("✅ Start text updated.")
    else:
        await message.answer("✏️ নতুন Start Text পাঠান অথবা /editstart <text> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_edit_start)

@router.message(AdminState.waiting_edit_start)
async def process_edit_start(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    await set_setting("start_text", message.text)
    await add_audit_log(message.from_user.id, "edit_start", message.text[:50])
    await message.answer("✅ Start text updated.")
    await state.clear()

@router.message(Command("setofficial"))
async def cmd_setofficial(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    args = message.text.split(maxsplit=1)
    if len(args) > 1 and args[1].strip():
        url = args[1].strip()
        if is_valid_url(url):
            await set_setting("official_group", url)
            await add_audit_log(message.from_user.id, "set_official", url)
            await message.answer("✅ Official Group link updated.")
        else:
            await message.answer("❌ Invalid URL.")
    else:
        await message.answer("🔗 Official Group-এর নতুন URL পাঠান অথবা /setofficial <url> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_edit_official)

@router.message(AdminState.waiting_edit_official)
async def process_edit_official(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    if message.text and is_valid_url(message.text):
        await set_setting("official_group", message.text.strip())
        await add_audit_log(message.from_user.id, "set_official", message.text)
        await message.answer("✅ Official Group link updated.")
        await state.clear()
    else:
        await message.answer("❌ Invalid URL.")

@router.message(Command("setbaby"))
async def cmd_setbaby(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    args = message.text.split(maxsplit=1)
    if len(args) > 1 and args[1].strip():
        url = args[1].strip()
        if is_valid_url(url):
            await set_setting("baby_group", url)
            await add_audit_log(message.from_user.id, "set_baby", url)
            await message.answer("✅ Baby Video Group link updated.")
        else:
            await message.answer("❌ Invalid URL.")
    else:
        await message.answer("🔗 Baby Video Group-এর নতুন URL পাঠান অথবা /setbaby <url> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_edit_baby)

@router.message(AdminState.waiting_edit_baby)
async def process_edit_baby(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    if message.text and is_valid_url(message.text):
        await set_setting("baby_group", message.text.strip())
        await add_audit_log(message.from_user.id, "set_baby", message.text)
        await message.answer("✅ Baby Video Group link updated.")
        await state.clear()
    else:
        await message.answer("❌ Invalid URL.")

@router.message(Command("maintenance"))
async def cmd_maintenance(message: Message):
    if not await is_admin(message.from_user.id):
        return
    parts = message.text.split()
    if len(parts) == 2 and parts[1].lower() in ("on", "off"):
        mode = parts[1].lower()
        await set_setting("maintenance_mode", "1" if mode == "on" else "0")
        await add_audit_log(message.from_user.id, "maintenance", mode)
        await message.answer(f"✅ Maintenance mode turned {'ON' if mode == 'on' else 'OFF'}.")
    else:
        await message.answer("Usage: /maintenance on|off")

@router.message(Command("uptime"))
async def cmd_uptime(message: Message):
    if not await is_admin(message.from_user.id):
        return
    await show_uptime(message)

@router.message(Command("backup"))
async def cmd_backup(message: Message):
    if not await is_admin(message.from_user.id):
        return
    if not Path(DB_PATH).exists():
        await message.answer("❌ Database file not found.")
        return
    await message.answer_document(open(DB_PATH, "rb"), filename="bot.db")

@router.message(Command("cancel"))
async def cmd_cancel(message: Message, state: FSMContext):
    current = await state.get_state()
    if current:
        await state.clear()
        await message.answer("✅ Action cancelled.")
    else:
        await message.answer("No active action to cancel.")

# ==================== INLINE QUERY ====================
@router.inline_query()
async def inline_query_handler(inline_query: InlineQuery):
    official = settings_cache.get("official_group", OFFICIAL_GROUP)
    baby = settings_cache.get("baby_group", BABY_VIDEO_GROUP)
    results = [
        InlineQueryResultArticle(
            id="official",
            title="Official Group",
            input_message_content=InputTextMessageContent(
                message_text=f"Join our Official Group: {official}",
                disable_web_page_preview=False,
            ),
            reply_markup=InlineKeyboardMarkup(
                inline_keyboard=[[InlineKeyboardButton(text="Join Official", url=official)]]
            ),
            description="Official Group link",
        ),
        InlineQueryResultArticle(
            id="baby",
            title="Baby Video Group",
            input_message_content=InputTextMessageContent(
                message_text=f"Join Baby Video Group: {baby}",
                disable_web_page_preview=False,
            ),
            reply_markup=InlineKeyboardMarkup(
                inline_keyboard=[[InlineKeyboardButton(text="Join Baby Video", url=baby)]]
            ),
            description="Baby Video Collection Group link",
        ),
    ]
    await inline_query.answer(results, cache_time=0, is_personal=True)

# ==================== FALLBACK ====================
@router.message(F.chat.type == "private")
async def fallback(message: Message):
    await message.answer(
        "👋 গ্রুপে যেতে নিচের বাটন ব্যবহার করুন।",
        reply_markup=start_keyboard(),
        disable_web_page_preview=True,
    )

# ==================== CALLBACK HANDLERS ====================
@router.callback_query(F.data == "refresh_start")
async def refresh_start(callback: CallbackQuery):
    try:
        await callback.message.edit_text(await get_setting("start_text"), reply_markup=start_keyboard(), disable_web_page_preview=True)
    except TelegramBadRequest:
        pass
    await callback.answer("✅ Updated")

@router.callback_query(F.data == "admin_back")
async def admin_back(callback: CallbackQuery, state: FSMContext):
    await state.clear()
    try:
        await callback.message.edit_text(ADMIN_PANEL_TEXT, reply_markup=admin_keyboard())
    except TelegramBadRequest:
        await callback.message.answer(ADMIN_PANEL_TEXT, reply_markup=admin_keyboard())
    await callback.answer()

@router.callback_query(F.data == "admin_cancel")
async def admin_cancel_cb(callback: CallbackQuery, state: FSMContext):
    await state.clear()
    await callback.message.edit_text("✅ Action cancelled.")
    await callback.answer()
    await admin_back(callback, state)

@router.callback_query(F.data == "admin_stats")
async def admin_stats_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    await show_stats(callback.message, edit=True)
    await callback.answer()

@router.callback_query(F.data == "admin_broadcast_prompt")
async def admin_broadcast_prompt_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer(
        "📢 Broadcast করতে টেক্সট পাঠান, মিডিয়া reply দিয়ে পাঠান, অথবা /broadcast <text> ব্যবহার করুন।",
        reply_markup=cancel_keyboard(),
    )
    await state.set_state(AdminState.waiting_broadcast_text)
    await callback.answer()

@router.callback_query(F.data == "admin_edit_start")
async def admin_edit_start_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("✏️ নতুন Start Text পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_edit_start)
    await callback.answer()

@router.callback_query(F.data == "admin_edit_links")
async def admin_edit_links_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    keyboard = InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="Edit Official", callback_data="admin_edit_official")],
            [InlineKeyboardButton(text="Edit Baby Video", callback_data="admin_edit_baby")],
            [InlineKeyboardButton(text="⬅️ Back", callback_data="admin_back")],
        ]
    )
    await callback.message.edit_text("🔗 কোন লিংক পরিবর্তন করবেন?", reply_markup=keyboard)
    await callback.answer()

@router.callback_query(F.data == "admin_edit_official")
async def admin_edit_official_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("🔗 Official Group-এর নতুন URL পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_edit_official)
    await callback.answer()

@router.callback_query(F.data == "admin_edit_baby")
async def admin_edit_baby_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("🔗 Baby Video Group-এর নতুন URL পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_edit_baby)
    await callback.answer()

@router.callback_query(F.data == "admin_maintenance_menu")
async def admin_maintenance_menu_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    status = await is_maintenance_mode()
    keyboard = InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="✅ Turn ON" if not status else "🟢 ON (current)", callback_data="admin_maintenance_on"),
             InlineKeyboardButton(text="❌ Turn OFF" if status else "🔴 OFF (current)", callback_data="admin_maintenance_off")],
            [InlineKeyboardButton(text="✏️ Custom Message", callback_data="admin_maintenance_message")],
            [InlineKeyboardButton(text="⬅️ Back", callback_data="admin_back")],
        ]
    )
    await callback.message.edit_text(f"🔧 Maintenance Mode is currently <b>{'ON' if status else 'OFF'}</b>", reply_markup=keyboard)
    await callback.answer()

@router.callback_query(F.data == "admin_maintenance_on")
async def admin_maintenance_on_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    await set_setting("maintenance_mode", "1")
    await add_audit_log(callback.from_user.id, "maintenance", "on")
    await callback.message.edit_text("✅ Maintenance Mode ON.")
    await callback.answer()
    await asyncio.sleep(1)
    await admin_back(callback, None)

@router.callback_query(F.data == "admin_maintenance_off")
async def admin_maintenance_off_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    await set_setting("maintenance_mode", "0")
    await add_audit_log(callback.from_user.id, "maintenance", "off")
    await callback.message.edit_text("✅ Maintenance Mode OFF.")
    await callback.answer()
    await asyncio.sleep(1)
    await admin_back(callback, None)

@router.callback_query(F.data == "admin_maintenance_message")
async def admin_maintenance_message_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("✏️ Maintenance Message পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_maintenance_message)
    await callback.answer()

@router.message(AdminState.waiting_maintenance_message)
async def process_maintenance_message(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Cancelled.")
        return
    await set_setting("maintenance_message", message.text)
    await add_audit_log(message.from_user.id, "maintenance_msg", message.text[:50])
    await message.answer("✅ Maintenance Message updated.")
    await state.clear()

@router.callback_query(F.data == "admin_add_admin")
async def admin_add_admin_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("➕ নতুন অ্যাডমিনের Telegram ID পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_add_admin)
    await callback.answer()

@router.callback_query(F.data == "admin_remove_admin")
async def admin_remove_admin_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("➖ Remove করতে অ্যাডমিনের Telegram ID পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_remove_admin)
    await callback.answer()

@router.callback_query(F.data == "admin_user_lookup")
async def admin_user_lookup_cb(callback: CallbackQuery, state: FSMContext):
    if not await is_admin(callback.from_user.id):
        return
    await callback.message.answer("🔍 লুকআপ করতে Telegram ID পাঠান।", reply_markup=cancel_keyboard())
    await state.set_state(AdminState.waiting_user_lookup)
    await callback.answer()

@router.callback_query(F.data == "admin_broadcast_history")
async def admin_broadcast_history_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    history = await get_broadcast_history(limit=10)
    if not history:
        await callback.message.answer("📈 কোনো broadcast history নেই।")
        await callback.answer()
        return
    text = "📈 <b>Last Broadcasts</b>\n\n"
    for h in history:
        text += f"#{h['id']} | {h['type']} | Sent: {h['sent']}/{h['total']} | Fail: {h['failed']} | Blocked: {h['blocked']}\n"
    await callback.message.answer(text)
    await callback.answer()

@router.callback_query(F.data == "admin_clean_dead")
async def admin_clean_dead_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    dead = await get_dead_users()
    if dead == 0:
        await callback.message.answer("🧹 কোনো dead user নেই।")
    else:
        await callback.message.answer(f"🧹 Currently {dead} dead users marked.\nFuture broadcasts exclude them.")
    await callback.answer()

@router.callback_query(F.data == "admin_backup")
async def admin_backup_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    if not Path(DB_PATH).exists():
        await callback.message.answer("❌ Database file not found.")
        return
    await callback.message.answer_document(open(DB_PATH, "rb"), filename="bot.db")
    await callback.answer()

@router.callback_query(F.data == "admin_uptime")
async def admin_uptime_cb(callback: CallbackQuery):
    if not await is_admin(callback.from_user.id):
        return
    await show_uptime(callback.message)
    await callback.answer()

# ==================== BROADCAST HELPERS ====================
@router.message(Command("broadcast"))
async def cmd_broadcast(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.reply_to_message and (
        message.reply_to_message.photo
        or message.reply_to_message.video
        or message.reply_to_message.animation
        or message.reply_to_message.document
        or message.reply_to_message.audio
        or message.reply_to_message.voice
    ):
        await start_broadcast_media(message, message.reply_to_message)
        return
    args = message.text.split(maxsplit=1)
    if len(args) > 1 and args[1].strip():
        await start_broadcast_text(message, args[1].strip())
    else:
        await message.answer("📢 ব্রডকাস্ট করতে টেক্সট পাঠান অথবা /broadcast <text> ব্যবহার করুন।", reply_markup=cancel_keyboard())
        await state.set_state(AdminState.waiting_broadcast_text)

@router.message(AdminState.waiting_broadcast_text)
async def process_broadcast_text(message: Message, state: FSMContext):
    if not await is_admin(message.from_user.id):
        return
    if message.text and message.text.lower() == "/cancel":
        await state.clear()
        await message.answer("✅ Broadcast cancelled.")
        return
    await start_broadcast_text(message, message.text)
    await state.clear()

async def start_broadcast_text(message: Message, text: str):
    global broadcast_event, broadcast_task
    users = await get_all_broadcast_users()
    if not users:
        await message.answer("❌ কোনো eligible user নেই।")
        return

    if broadcast_task and not broadcast_task.done():
        await message.answer("⚠️ ইতিমধ্যে একটি broadcast চলছে। /cancelbroadcast দিয়ে বাতিল করতে পারবেন।")
        return

    broadcast_event = asyncio.Event()
    sent = failed = blocked = 0
    total = len(users)
    progress_msg = await message.answer(f"📢 Broadcast শুরু হচ্ছে...\n\n👥 মোট: {total}\n✅ Sent: 0\n❌ Failed: 0\n🚫 Blocked: 0")

    async def worker(uid: int):
        nonlocal sent, failed, blocked
        if broadcast_event.is_set():
            return
        try:
            await message.bot.send_message(uid, text, disable_web_page_preview=True)
            await mark_user_alive(uid)
            sent += 1
        except TelegramRetryAfter as e:
            logger.warning("RetryAfter %s for user %s", e.retry_after, uid)
            await asyncio.sleep(e.retry_after)
            if broadcast_event.is_set():
                return
            try:
                await message.bot.send_message(uid, text, disable_web_page_preview=True)
                await mark_user_alive(uid)
                sent += 1
            except TelegramForbiddenError:
                await mark_user_dead(uid, "blocked")
                blocked += 1
            except Exception as e2:
                await mark_user_dead(uid, str(e2))
                failed += 1
        except TelegramForbiddenError:
            await mark_user_dead(uid, "blocked")
            blocked += 1
        except Exception as e:
            await mark_user_dead(uid, str(e))
            failed += 1

    async def run_broadcast():
        semaphore = asyncio.Semaphore(BROADCAST_CONCURRENCY)
        async def sem_worker(uid):
            async with semaphore:
                await worker(uid)
        await asyncio.gather(*(sem_worker(uid) for uid in users))
        if not broadcast_event.is_set():
            await add_broadcast_record(message.from_user.id, sent, failed, blocked, total, "text", text[:100])
            await progress_msg.edit_text(
                f"✅ <b>Broadcast Complete</b>\n\n"
                f"👥 Total: {total}\n"
                f"✅ Sent: {sent}\n"
                f"❌ Failed: {failed}\n"
                f"🚫 Blocked: {blocked}"
            )
        else:
            await progress_msg.edit_text("🛑 Broadcast cancelled.")

    broadcast_task = asyncio.create_task(run_broadcast())
    await broadcast_task

@router.message(Command("cancelbroadcast"))
async def cmd_cancelbroadcast(message: Message):
    if not await is_admin(message.from_user.id):
        return
    global broadcast_event
    if broadcast_event and not broadcast_event.is_set():
        broadcast_event.set()
        await message.answer("🛑 Broadcast cancellation requested.")
    else:
        await message.answer("❌ কোনো সক্রিয় broadcast নেই।")

async def start_broadcast_media(message: Message, reply_message: Message):
    global broadcast_event, broadcast_task
    users = await get_all_broadcast_users()
    if not users:
        await message.answer("❌ কোনো eligible user নেই।")
        return

    if broadcast_task and not broadcast_task.done():
        await message.answer("⚠️ ইতিমধ্যে একটি broadcast চলছে। /cancelbroadcast দিয়ে বাতিল করতে পারবেন।")
        return

    broadcast_event = asyncio.Event()
    sent = failed = blocked = 0
    total = len(users)
    progress_msg = await message.answer(f"📢 Media Broadcast শুরু হচ্ছে...\n\n👥 মোট: {total}\n✅ Sent: 0\n❌ Failed: 0\n🚫 Blocked: 0")

    async def worker(uid: int):
        nonlocal sent, failed, blocked
        if broadcast_event.is_set():
            return
        try:
            await message.bot.copy_message(
                chat_id=uid,
                from_chat_id=message.chat.id,
                message_id=reply_message.message_id,
            )
            await mark_user_alive(uid)
            sent += 1
        except TelegramRetryAfter as e:
            logger.warning("RetryAfter %s for user %s", e.retry_after, uid)
            await asyncio.sleep(e.retry_after)
            if broadcast_event.is_set():
                return
            try:
                await message.bot.copy_message(
                    chat_id=uid,
                    from_chat_id=message.chat.id,
                    message_id=reply_message.message_id,
                )
                await mark_user_alive(uid)
                sent += 1
            except TelegramForbiddenError:
                await mark_user_dead(uid, "blocked")
                blocked += 1
            except Exception as e2:
                await mark_user_dead(uid, str(e2))
                failed += 1
        except TelegramForbiddenError:
            await mark_user_dead(uid, "blocked")
            blocked += 1
        except Exception as e:
            await mark_user_dead(uid, str(e))
            failed += 1

    async def run_broadcast():
        semaphore = asyncio.Semaphore(BROADCAST_CONCURRENCY)
        async def sem_worker(uid):
            async with semaphore:
                await worker(uid)
        await asyncio.gather(*(sem_worker(uid) for uid in users))
        if not broadcast_event.is_set():
            await add_broadcast_record(message.from_user.id, sent, failed, blocked, total, "media", str(reply_message.message_id))
            await progress_msg.edit_text(
                f"✅ <b>Media Broadcast Complete</b>\n\n"
                f"👥 Total: {total}\n"
                f"✅ Sent: {sent}\n"
                f"❌ Failed: {failed}\n"
                f"🚫 Blocked: {blocked}"
            )
        else:
            await progress_msg.edit_text("🛑 Broadcast cancelled.")

    broadcast_task = asyncio.create_task(run_broadcast())
    await broadcast_task

# ==================== STATS & UPTIME ====================
async def show_stats(message_or_callback: Message, edit: bool = False):
    total, banned, dead, active24, active7, new_today, total_cmds = await asyncio.gather(
        get_total_users(),
        get_banned_users(),
        get_dead_users(),
        get_active_24h(),
        get_active_7d(),
        get_new_today(),
        get_total_commands(),
    )
    text = f"""
📊 <b>Statistics</b>

👥 Total Users: <b>{total}</b>
⛔ Banned: <b>{banned}</b>
🧟 Dead: <b>{dead}</b>
🕐 Active 24h: <b>{active24}</b>
🗓 Active 7d: <b>{active7}</b>
🆕 New Today: <b>{new_today}</b>
📈 Total Commands: <b>{total_cmds}</b>
"""
    if edit:
        await message_or_callback.edit_text(text)
    else:
        await message_or_callback.answer(text)

async def show_uptime(message: Message):
    uptime_seconds = int(time.time() - start_time)
    hours, rem = divmod(uptime_seconds, 3600)
    minutes, seconds = divmod(rem, 60)
    text = (
        f"⏱ <b>Uptime</b>: {hours}h {minutes}m {seconds}s\n"
        f"⚡ Bot started at: {datetime.fromtimestamp(start_time, tz=timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}"
    )
    await message.answer(text)

# ==================== ERROR HANDLER ====================
@router.errors()
async def error_handler(update: Update, exception: Exception):
    logger.error("Update %s caused exception: %s", update, exception, exc_info=True)
    return True

# ==================== HTTP SERVER ====================
async def handle_health(request):
    uptime = int(time.time() - start_time)
    db_status = "ok" if db_conn is not None else "not_initialized"
    return web.json_response({"status": "ok", "uptime": uptime, "database": db_status})

async def handle_webhook(request, bot: Bot, dp: Dispatcher):
    if WEBHOOK_SECRET:
        secret = request.headers.get("X-Telegram-Bot-Api-Secret-Token", "")
        if secret != WEBHOOK_SECRET:
            return web.Response(status=401)
    try:
        update = await request.json()
        await dp.feed_webhook_update(bot, update)
        return web.Response(status=200)
    except Exception as e:
        logger.error("Webhook update error: %s", e)
        return web.Response(status=500)

def create_http_app(webhook_mode: bool, bot: Bot, dp: Dispatcher) -> web.Application:
    app = web.Application()
    app.router.add_get("/health", handle_health)
    if webhook_mode:
        app.router.add_post(WEBHOOK_PATH, lambda request: handle_webhook(request, bot, dp))
    return app

async def run_http_server(host: str, port: int, webhook_mode: bool = False, bot: Bot = None, dp: Dispatcher = None):
    app = create_http_app(webhook_mode, bot, dp)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, host, port)
    await site.start()
    logger.info("HTTP server started on %s:%s", host, port)
    await asyncio.Event().wait()

# ==================== MAIN ====================
async def main():
    if not BOT_TOKEN:
        raise RuntimeError("BOT_TOKEN is not set in environment variables!")

    bot = Bot(token=BOT_TOKEN, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher(storage=MemoryStorage())
    dp.include_router(router)

    await init_db()
    await load_admins()

    await bot.set_my_commands([
        BotCommand(command="start", description="গ্রুপের লিংক দেখুন"),
        BotCommand(command="help", description="সাহায্য"),
        BotCommand(command="ping", description="বট স্ট্যাটাস"),
        BotCommand(command="id", description="আপনার ID দেখুন"),
        BotCommand(command="admin", description="Admin Panel"),
    ])

    try:
        me = await bot.get_me()
        logger.info("Starting @%s (%s)", me.username, me.id)

        await bot.delete_webhook(drop_pending_updates=False)

        if WEBHOOK_MODE:
            if not WEBHOOK_URL:
                raise RuntimeError("WEBHOOK_URL is not set while WEBHOOK_MODE=true")
            await bot.set_webhook(WEBHOOK_URL, secret_token=WEBHOOK_SECRET or None)
            await run_http_server(WEBHOOK_HOST, WEBHOOK_PORT, webhook_mode=True, bot=bot, dp=dp)
        else:
            health_task = asyncio.create_task(run_http_server(HEALTH_HOST, HEALTH_PORT))
            await dp.start_polling(bot, allowed_updates=dp.resolve_used_update_types())
            health_task.cancel()

    except Exception:
        logger.exception("Fatal bot error")
        raise
    finally:
        await db_close()
        await bot.session.close()
        logger.info("Bot stopped cleanly.")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except (KeyboardInterrupt, SystemExit):
        logger.info("Stopped by user/system.")