import asyncio
import logging
import os
import time
from collections import defaultdict, deque
from html import escape

from aiogram import Bot, Dispatcher, F, Router, types
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError, TelegramRetryAfter
from aiogram.filters import Command
from aiogram.client.default import DefaultBotProperties
from aiogram.types import (
    BotCommand,
    CallbackQuery,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
)

# =========================================================
# CONFIG
# =========================================================

BOT_TOKEN = os.getenv("BOT_TOKEN", "").strip()

OFFICIAL_GROUP = os.getenv(
    "OFFICIAL_GROUP",
    "https://t.me/+Q9q5Fijwa8BiZGY0",
).strip()

BABY_VIDEO_GROUP = os.getenv(
    "BABY_VIDEO_GROUP",
    "https://t.me/+rwfWJC3jRMNhYThk",
).strip()

# Simple anti-spam: max commands/messages in window.
RATE_LIMIT_COUNT = int(os.getenv("RATE_LIMIT_COUNT", "6"))
RATE_LIMIT_WINDOW = int(os.getenv("RATE_LIMIT_WINDOW", "10"))

START_TEXT = f"""
🎬 <b>যারা বাচ্চাদের সুন্দর ভিডিও কালেকশন খুঁজছিলেন, তাদের জন্য আমাদের গ্রুপ 👆</b>

👶🔥 <b>Baby Video Collection ❤️</b>
নতুন নতুন সুন্দর ভিডিও পেতে এখনই জয়েন করুন।

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

নিচের বাটন ব্যবহার করে সরাসরি গ্রুপে জয়েন করতে পারবেন।
""".strip()

router = Router()

# user_id -> timestamps
_rate_state: dict[int, deque[float]] = defaultdict(deque)


# =========================================================
# LOGGING
# =========================================================

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("baby-video-bot")


# =========================================================
# HELPERS
# =========================================================

def start_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text="🔥 Official Group",
                    url=OFFICIAL_GROUP,
                )
            ],
            [
                InlineKeyboardButton(
                    text="👶 Baby Video Group",
                    url=BABY_VIDEO_GROUP,
                )
            ],
            [
                InlineKeyboardButton(
                    text="🔄 আবার দেখুন",
                    callback_data="refresh_start",
                )
            ],
        ]
    )


def is_rate_limited(user_id: int) -> bool:
    now = time.monotonic()
    q = _rate_state[user_id]

    while q and now - q[0] > RATE_LIMIT_WINDOW:
        q.popleft()

    if len(q) >= RATE_LIMIT_COUNT:
        return True

    q.append(now)
    return False


async def safe_answer(
    message: types.Message,
    text: str,
    *,
    reply_markup: InlineKeyboardMarkup | None = None,
) -> None:
    try:
        await message.answer(
            text,
            reply_markup=reply_markup,
            disable_web_page_preview=True,
        )
    except TelegramRetryAfter as e:
        logger.warning("Flood control. Waiting %s seconds", e.retry_after)
        await asyncio.sleep(e.retry_after)
        await message.answer(
            text,
            reply_markup=reply_markup,
            disable_web_page_preview=True,
        )


# =========================================================
# HANDLERS
# =========================================================

@router.message(Command("start"))
async def cmd_start(message: types.Message) -> None:
    user = message.from_user
    if user and is_rate_limited(user.id):
        await message.answer("⏳ একটু ধীরে চেষ্টা করুন।")
        return

    logger.info(
        "START user_id=%s username=%s",
        user.id if user else None,
        user.username if user else None,
    )

    await safe_answer(
        message,
        START_TEXT,
        reply_markup=start_keyboard(),
    )


@router.message(Command("help"))
async def cmd_help(message: types.Message) -> None:
    await safe_answer(
        message,
        HELP_TEXT,
        reply_markup=start_keyboard(),
    )


@router.message(Command("ping"))
async def cmd_ping(message: types.Message) -> None:
    started = time.perf_counter()
    sent = await message.answer("🏓 Checking...")
    latency_ms = int((time.perf_counter() - started) * 1000)

    try:
        await sent.edit_text(f"✅ Bot is online\n⚡ Response: {latency_ms} ms")
    except TelegramBadRequest:
        pass


@router.callback_query(F.data == "refresh_start")
async def refresh_start(callback: CallbackQuery) -> None:
    try:
        await callback.message.edit_text(
            START_TEXT,
            reply_markup=start_keyboard(),
            disable_web_page_preview=True,
        )
    except TelegramBadRequest:
        # Usually means message content was unchanged.
        pass

    await callback.answer("✅ Updated")


@router.message()
async def fallback(message: types.Message) -> None:
    user = message.from_user
    if user and is_rate_limited(user.id):
        return

    await safe_answer(
        message,
        "👋 গ্রুপে যেতে নিচের বাটন ব্যবহার করুন।",
        reply_markup=start_keyboard(),
    )


# =========================================================
# BOT LIFECYCLE
# =========================================================

async def set_commands(bot: Bot) -> None:
    await bot.set_my_commands(
        [
            BotCommand(command="start", description="গ্রুপের লিংক দেখুন"),
            BotCommand(command="help", description="সাহায্য"),
            BotCommand(command="ping", description="বট স্ট্যাটাস"),
        ]
    )


async def main() -> None:
    if not BOT_TOKEN:
        raise RuntimeError(
            "BOT_TOKEN পাওয়া যায়নি। Render/VPS Environment Variable-এ BOT_TOKEN সেট করুন।"
        )

    bot = Bot(
        token=BOT_TOKEN,
        default=DefaultBotProperties(parse_mode=ParseMode.HTML),
    )
    dp = Dispatcher()
    dp.include_router(router)

    try:
        me = await bot.get_me()
        logger.info("Starting @%s (%s)", me.username, me.id)

        await set_commands(bot)

        # Prevent webhook/polling conflict after moving between hosts.
        await bot.delete_webhook(drop_pending_updates=False)

        await dp.start_polling(
            bot,
            allowed_updates=dp.resolve_used_update_types(),
        )

    except TelegramForbiddenError:
        logger.exception("Bot token is invalid/revoked or bot access is forbidden.")
        raise
    except Exception:
        logger.exception("Fatal bot error")
        raise
    finally:
        await bot.session.close()
        logger.info("Bot stopped cleanly.")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except (KeyboardInterrupt, SystemExit):
        logger.info("Stopped by user/system.")