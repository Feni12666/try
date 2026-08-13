import asyncio
from aiogram import Bot, Dispatcher, types
from aiogram.filters import Command

BOT_TOKEN = "8900741068:AAF2dWLL6-3xvVyso5sw42GCbKEuJkPmuYQ"

bot = Bot(token=BOT_TOKEN)
dp = Dispatcher()

START_TEXT = """
🎬 যারা বাচ্চাদের সুন্দর ভিডিও কালেকশন খুঁজছিলেন, তাদের জন্য আমাদের গ্রুপ 👆

👶🔥 Baby Video Collection ❤️
নতুন নতুন সুন্দর ভিডিও পেতে এখনই জয়েন করুন।

👇 আমাদের Official Group 👇
https://t.me/+Q9q5Fijwa8BiZGY0

👇 Baby Video Collection Group 👇
https://t.me/+rwfWJC3jRMNhYThk

❤️ সবাইকে স্বাগতম
""" 

@dp.message(Command("start"))
async def start(message: types.Message):
    keyboard = types.InlineKeyboardMarkup(
        inline_keyboard=[
            [
                types.InlineKeyboardButton(
                    text="🔥 Official Group",
                    url="https://t.me/+Q9q5Fijwa8BiZGY0"
                )
            ],
            [
                types.InlineKeyboardButton(
                    text="👶 Baby Video Group",
                    url="https://t.me/+rwfWJC3jRMNhYThk"
                )
            ]
        ]
    )

    await message.answer(
        START_TEXT,
        reply_markup=keyboard
    )


async def main():
    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())